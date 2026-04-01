package ru.practicum.request.service;

import feign.FeignException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.request.client.EventClient;
import ru.practicum.request.client.UserClient;
import ru.practicum.request.dto.EventFullDto;
import ru.practicum.request.dto.EventRequestStatusUpdateDto;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.enums.EventState;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.mapper.ParticipationRequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.request.exception.ConflictException;
import ru.practicum.request.exception.DuplicatedException;
import ru.practicum.request.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {

    private final ParticipationRequestRepository repository;
    private final UserClient userClient;
    private final EventClient eventClient;
    private final ParticipationRequestMapper mapper;

    private void checkUserExists(Long userId) {
        try {
            userClient.getUserById(userId);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new NotFoundException("User with id=" + userId + " not found");
            }
            throw e;
        }
    }

    private EventFullDto checkEventExists(Long eventId) {
        try {
            return eventClient.getEventById(eventId);
        } catch (FeignException e) {
            if (e.status() == 404) {
                throw new NotFoundException("Event with id=" + eventId + " not found");
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId) {
        log.info("Creating request: userId={}, eventId={}", userId, eventId);

        // Проверяем существование пользователя
        checkUserExists(userId);

        // Получаем событие
        EventFullDto event = checkEventExists(eventId);

        // Проверяем, не создавал ли пользователь уже заявку
        if (repository.findByEventIdAndRequesterId(eventId, userId).isPresent()) {
            throw new DuplicatedException("Request already exists");
        }

        // Проверяем, что пользователь не является инициатором
        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Event initiator cannot participate in their own event");
        }

        // Проверяем, что событие опубликовано
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        // Проверяем лимит участников
        int confirmedCount = repository.findAllByEventIdAndStatus(eventId, RequestStatus.CONFIRMED).size();
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictException("Participant limit has been reached");
        }

        // Определяем статус
        RequestStatus status = RequestStatus.PENDING;
        if (event.getParticipantLimit() == 0 || !event.getRequestModeration()) {
            status = RequestStatus.CONFIRMED;
        }

        // Создаем заявку
        ParticipationRequest request = ParticipationRequest.builder()
                .requesterId(userId)
                .eventId(eventId)
                .status(status)
                .created(LocalDateTime.now())
                .build();

        ParticipationRequest saved = repository.save(request);
        log.info("Request created: {}", saved);

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

        if (!request.getRequesterId().equals(userId)) {
            throw new ConflictException("Cannot cancel another user's request");
        }

        request.setStatus(RequestStatus.CANCELED);
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

        checkUserExists(userId);
        EventFullDto event = checkEventExists(eventId);

        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Only event initiator can change request status");
        }

        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot change request status for unpublished event");
        }

        List<ParticipationRequest> requests = repository.findAllById(updateDto.getRequestIds());

        if (requests.size() != updateDto.getRequestIds().size()) {
            throw new NotFoundException("Some requests not found");
        }

        int confirmedCount = repository.findAllByEventIdAndStatus(eventId, RequestStatus.CONFIRMED).size();
        int availableSlots = event.getParticipantLimit() - confirmedCount;

        List<ParticipationRequest> confirmed = new ArrayList<>();
        List<ParticipationRequest> rejected = new ArrayList<>();

        for (ParticipationRequest request : requests) {
            if (request.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request status must be PENDING");
            }

            if (updateDto.getStatus() == RequestStatus.CONFIRMED) {
                if (availableSlots > 0) {
                    request.setStatus(RequestStatus.CONFIRMED);
                    confirmed.add(request);
                    availableSlots--;
                } else {
                    request.setStatus(RequestStatus.REJECTED);
                    rejected.add(request);
                }
            } else if (updateDto.getStatus() == RequestStatus.REJECTED) {
                request.setStatus(RequestStatus.REJECTED);
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
}