package ru.practicum.request.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.request.client.EventClient;
import ru.practicum.request.client.UserClient;
import ru.practicum.request.dto.EventFullDto;
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
        // Проверяем существование пользователя через Feign
        UserDto userDto = userClient.getUserById(userId);

        // Получаем событие через Feign
        EventFullDto event = eventClient.getEventById(eventId);

        // Проверяем, не создавал ли пользователь уже заявку на это событие
        if (repository.findByEventIdAndRequesterId(eventId, userId).isPresent()) {
            throw new DuplicatedException("Такая заявка уже создана");
        }

        // Проверяем, что пользователь не является инициатором события
        if (event.getInitiatorId().equals(userId)) {
            throw new ConflictException("Инициатор события не может добавить запрос на участие в своём событии");
        }

        // Проверяем, что событие опубликовано
        if (event.getParticipantLimit() != 0 && event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }

        // Проверяем лимит участников
        int confirmedRequestsCount = repository.findAllByEventIdAndStatus(eventId, RequestStatus.CONFIRMED).size();
        if (event.getParticipantLimit() > 0 && confirmedRequestsCount >= event.getParticipantLimit()) {
            throw new ConflictException("Достигнут лимит запросов на участие");
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

        ParticipationRequest participationRequest = repository.save(request);
        repository.flush();
        log.info("Запрос успешно создан. Параметры: {}", participationRequest);

        return mapper.toDto(participationRequest);
    }

    @Override
    public List<ParticipationRequestDto> getRequests(Long userId) {
        // Проверяем существование пользователя через Feign
        userClient.getUserById(userId);

        return repository.findAllByRequesterId(userId).stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = repository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Заявка не найдена"));

        // Проверяем, что заявка принадлежит пользователю
        if (!request.getRequesterId().equals(userId)) {
            log.error("Попытка отменить чужую заявку: userId={}, заявка принадлежит userId={}", userId, request.getRequesterId());
            throw new ConflictException("Пользователь, который не является автором заявки, не может её отменить.");
        }

        request.setStatus(RequestStatus.CANCELED);
        log.info("Статус заявки с id={} изменен на CANCELED", requestId);

        ParticipationRequestDto requestDto = mapper.toDto(repository.save(request));
        repository.flush();
        log.info("Участие в событии для пользователя с id={} отменено", userId);

        return requestDto;
    }
}