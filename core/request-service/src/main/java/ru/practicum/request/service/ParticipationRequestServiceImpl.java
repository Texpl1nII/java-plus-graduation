package ru.practicum.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.request.client.CollectorGrpcClient;
import ru.practicum.request.client.EventClient;
import ru.practicum.request.client.UserClient;
import ru.practicum.request.dto.EventFullDto;
import ru.practicum.request.dto.EventRequestStatusUpdateDto;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.exception.BadRequestException;
import ru.practicum.request.exception.ConflictException;
import ru.practicum.request.exception.NotFoundException;
import ru.practicum.request.limit.ParticipantLimitChecker;
import ru.practicum.request.mapper.ParticipationRequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.request.status.RequestStatusManager;
import ru.practicum.request.validator.RequestValidator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {

    private final ParticipationRequestRepository repository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final ParticipationRequestMapper mapper;
    private final RequestValidator validator;
    private final RequestStatusManager statusManager;
    private final ParticipantLimitChecker limitChecker;
    private final CollectorGrpcClient collectorGrpcClient;

    private void checkUserExists(Long userId) {
        try {
            userClient.getUserById(userId);
        } catch (Exception e) {
            throw new NotFoundException("User with id=" + userId + " not found");
        }
    }

    private EventFullDto checkEventExists(Long eventId) {
        try {
            return eventClient.getEventById(eventId);
        } catch (Exception e) {
            throw new NotFoundException("Event with id=" + eventId + " not found");
        }
    }

    @Override
    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId) {
        log.info("Creating request: userId={}, eventId={}", userId, eventId);

        checkUserExists(userId);
        EventFullDto event = checkEventExists(eventId);

        // Валидация
        validator.validateNotDuplicate(eventId, userId);
        validator.validateNotInitiator(userId, event.getInitiatorId());
        validator.validateEventPublished(event);

        int confirmedCount = limitChecker.getConfirmedCount(eventId);
        validator.validateParticipantLimit(event, confirmedCount);

        // Создание запроса
        ParticipationRequest request = ParticipationRequest.builder()
                .requesterId(userId)
                .eventId(eventId)
                .status(statusManager.determineInitialStatus(event))
                .created(LocalDateTime.now())
                .build();

        ParticipationRequest saved = repository.save(request);
        log.info("Request created: {}", saved);

        // Отправляем REGISTER в Collector
        try {
            collectorGrpcClient.sendRegisterAction(userId, eventId);
            log.info("REGISTER action sent to Collector for userId={}, eventId={}", userId, eventId);
        } catch (Exception e) {
            log.error("Failed to send REGISTER action to Collector, but request was created successfully", e);
            // Не бросаем исключение - заявка уже создана
        }

        return mapper.toDto(saved);
    }

    @Override
    public List<ParticipationRequestDto> getRequests(Long userId) {
        log.info("Getting requests for user: {}", userId);
        checkUserExists(userId);

        return repository.findAllByRequesterId(userId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Cancelling request: userId={}, requestId={}", userId, requestId);

        ParticipationRequest request = repository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Request not found"));

        validator.validateRequestStatusForCancel(request, userId);
        statusManager.cancelRequest(request);

        ParticipationRequest saved = repository.save(request);
        log.info("Request cancelled: {}", saved);

        return mapper.toDto(saved);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Getting event requests: userId={}, eventId={}", userId, eventId);

        checkUserExists(userId);
        EventFullDto event = checkEventExists(eventId);

        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Only event initiator can view requests");
        }

        return repository.findAllByEventId(eventId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId,
                                                              EventRequestStatusUpdateDto updateDto) {
        log.info("Changing request status: userId={}, eventId={}, updateDto={}", userId, eventId, updateDto);

        if (updateDto == null || updateDto.getRequestIds() == null || updateDto.getRequestIds().isEmpty()) {
            throw new BadRequestException("Request IDs cannot be empty");
        }

        checkUserExists(userId);
        EventFullDto event = checkEventExists(eventId);

        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Only event initiator can change request status");
        }

        validator.validateEventPublished(event);

        List<ParticipationRequest> requests = repository.findAllById(updateDto.getRequestIds());
        if (requests.size() != updateDto.getRequestIds().size()) {
            throw new NotFoundException("Some requests not found");
        }

        limitChecker.checkAvailableSlots(event, updateDto.getRequestIds().size());

        List<ParticipationRequest> confirmed = new ArrayList<>();
        List<ParticipationRequest> rejected = new ArrayList<>();
        int availableSlots = limitChecker.calculateRemainingSlots(event);

        for (ParticipationRequest request : requests) {
            validator.validateRequestStatusForChange(request);

            if (updateDto.getStatus() == RequestStatus.CONFIRMED) {
                if (availableSlots > 0) {
                    statusManager.confirmRequest(request);
                    confirmed.add(request);
                    availableSlots--;
                } else {
                    statusManager.rejectRequest(request);
                    rejected.add(request);
                }
            } else if (updateDto.getStatus() == RequestStatus.REJECTED) {
                statusManager.rejectRequest(request);
                rejected.add(request);
            }
        }

        repository.saveAll(confirmed);
        repository.saveAll(rejected);

        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();
        result.setConfirmedRequests(confirmed.stream().map(mapper::toDto).collect(Collectors.toList()));
        result.setRejectedRequests(rejected.stream().map(mapper::toDto).collect(Collectors.toList()));

        log.info("Request statuses changed: confirmed={}, rejected={}", confirmed.size(), rejected.size());

        return result;
    }

    @Override
    public Long countByEventId(Long eventId) {
        log.info("Counting requests for event: {}", eventId);
        return repository.countByEventId(eventId);
    }

    @Override
    public Map<Long, Long> getConfirmedRequestsCounts(List<Long> eventIds) {
        log.info("Getting confirmed requests counts for events: {}", eventIds);
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }
        return repository.countByEventIdInAndStatus(eventIds, RequestStatus.CONFIRMED).stream()
                .collect(Collectors.toMap(
                        r -> r.getEventId(),
                        r -> r.getCount()
                ));
    }
}