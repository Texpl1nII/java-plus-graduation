package ru.practicum.request.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.DuplicatedException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.mapper.ParticipationRequestMapper;
import ru.practicum.user.model.User;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.user.dto.UserDto;
import ru.practicum.user.service.UserService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
@Transactional(readOnly = true)
public class ParticipationRequestServiceImpl implements ParticipationRequestService {

    private final ParticipationRequestRepository repository;
    private final UserService userService;
    private final ParticipationRequestMapper mapper;

    @Override
    @Transactional
    public ParticipationRequestDto create(Long userId, Long eventId, EventFullDto event) {
        if (repository.findByEventIdAndRequesterId(eventId, userId).isPresent()) {
            throw new DuplicatedException("Такая заявка уже создана");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор события не может добавить запрос на участие в своём событии");
        }

        if (event.getParticipantLimit() != 0 && event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }

        int confirmedRequestsCount = repository.findAllByEventIdAndStatus(eventId, RequestStatus.CONFIRMED).size();

        if (event.getParticipantLimit() > 0 && confirmedRequestsCount >= event.getParticipantLimit()) {
            throw new ConflictException("Достигнут лимит запросов на участие");
        }

        UserDto userDto = userService.getUser(userId);
        User user = new User();
        user.setId(userDto.getId());
        user.setName(userDto.getName());
        user.setEmail(userDto.getEmail());

        Event eventEntity = new Event();
        eventEntity.setId(event.getId());

        RequestStatus status = RequestStatus.PENDING;
        if (!event.getRequestModeration() || event.getParticipantLimit().equals(0)) {
            status = RequestStatus.CONFIRMED;
        }

        ParticipationRequest request = ParticipationRequest.builder()
                .requester(user)
                .event(eventEntity)
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
        UserDto user = userService.getUser(userId);
        return repository.findAllByRequesterId(user.getId()).stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        ParticipationRequest request = repository.findById(requestId).orElseThrow(() -> new NotFoundException("Заявка не найдена"));

        UserDto user = userService.getUser(request.getRequester().getId());
        if (!user.getId().equals(userId)) {
            log.error("Попытка отменить чужую заявку: userId={}, заявка принадлежит userId={}", userId, user.getId());
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