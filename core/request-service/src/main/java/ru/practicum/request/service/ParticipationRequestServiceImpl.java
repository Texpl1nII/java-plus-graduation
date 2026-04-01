package ru.practicum.request.service;

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
import ru.practicum.request.dto.UserDto;
import ru.practicum.request.enums.EventState;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.mapper.ParticipationRequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.DuplicatedException;
import ru.practicum.exception.NotFoundException;

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

    @Override
    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId) {
        log.info("Creating request: userId={}, eventId={}", userId, eventId);

        // Проверяем существование пользователя через Feign
        UserDto userDto = userClient.getUserById(userId);
        log.debug("User found: {}", userDto);

        // Получаем событие через Feign
        EventFullDto event = eventClient.getEventById(eventId);
        log.debug("Event found: {}", event);

        // Проверяем, не создавал ли пользователь уже заявку на это событие
        if (repository.findByEventIdAndRequesterId(eventId, userId).isPresent()) {
            throw new DuplicatedException("Request already exists");
        }

        // Проверяем, что пользователь не является инициатором события
        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Event initiator cannot participate in their own event");
        }

        // Проверяем, что событие опубликовано
        if (event.getParticipantLimit() != 0 && event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }

        // Проверяем лимит участников
        int confirmedRequestsCount = repository.findAllByEventIdAndStatus(eventId, RequestStatus.CONFIRMED).size();
        if (event.getParticipantLimit() > 0 && confirmedRequestsCount >= event.getParticipantLimit()) {
            throw new ConflictException("Participant limit has been reached");
        }

        // Определяем статус заявки
        RequestStatus status = RequestStatus.PENDING;
        if (!event.getRequestModeration() || event.getParticipantLimit().equals(0)) {
            status = RequestStatus.CONFIRMED;
        }

        // Создаем заявку
        ParticipationRequest request = ParticipationRequest.builder()
                .requesterId(userId)
                .eventId(eventId)
                .status(status)
                .created(LocalDateTime.now())
                .build();

        ParticipationRequest savedRequest = repository.save(request);
        log.info("Request created successfully: {}", savedRequest);

        return mapper.toDto(savedRequest);
    }

    @Override
    public List<ParticipationRequestDto> getRequests(Long userId) {
        log.info("Getting requests for user: {}", userId);

        // Проверяем существование пользователя через Feign
        userClient.getUserById(userId);

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

        // Проверяем, что заявка принадлежит пользователю
        if (!request.getRequesterId().equals(userId)) {
            log.error("Attempt to cancel another user's request: userId={}, requestOwnerId={}",
                    userId, request.getRequesterId());
            throw new ConflictException("Cannot cancel another user's request");
        }

        request.setStatus(RequestStatus.CANCELED);
        log.info("Request status changed to CANCELED: {}", request);

        ParticipationRequestDto requestDto = mapper.toDto(repository.save(request));
        log.info("Request cancelled successfully: {}", requestDto);

        return requestDto;
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Getting event requests: userId={}, eventId={}", userId, eventId);

        // Проверяем существование пользователя
        userClient.getUserById(userId);

        // Проверяем существование события
        EventFullDto event = eventClient.getEventById(eventId);

        // Проверяем, что пользователь является инициатором события
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

        // Проверяем существование пользователя
        userClient.getUserById(userId);

        // Получаем событие
        EventFullDto event = eventClient.getEventById(eventId);

        // Проверяем, что пользователь является инициатором события
        if (!event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Only event initiator can change request status");
        }

        // Проверяем, что событие опубликовано
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot change request status for unpublished event");
        }

        // Получаем все запросы
        List<ParticipationRequest> requests = repository.findAllById(updateDto.getRequestIds());

        // Проверяем, что все запросы существуют
        if (requests.size() != updateDto.getRequestIds().size()) {
            throw new NotFoundException("Some requests not found");
        }

        // Проверяем лимит участников для подтверждения
        int confirmedCount = repository.findAllByEventIdAndStatus(eventId, RequestStatus.CONFIRMED).size();
        int availableSlots = event.getParticipantLimit() - confirmedCount;

        List<ParticipationRequest> confirmed = new ArrayList<>();
        List<ParticipationRequest> rejected = new ArrayList<>();

        // Обрабатываем каждый запрос
        for (ParticipationRequest request : requests) {
            // Проверяем, что запрос в статусе PENDING
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

        // Сохраняем изменения
        repository.saveAll(confirmed);
        repository.saveAll(rejected);

        log.info("Request statuses changed: confirmed={}, rejected={}", confirmed.size(), rejected.size());

        // Формируем результат
        EventRequestStatusUpdateResult result = new EventRequestStatusUpdateResult();
        result.setConfirmedRequests(confirmed.stream().map(mapper::toDto).collect(Collectors.toList()));
        result.setRejectedRequests(rejected.stream().map(mapper::toDto).collect(Collectors.toList()));

        return result;
    }
}