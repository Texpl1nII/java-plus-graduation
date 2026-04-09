package ru.practicum.event.service;

import com.querydsl.core.BooleanBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatClient;
import feign.FeignException;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.assembler.EventDtoAssembler;
import ru.practicum.event.client.CategoryClient;
import ru.practicum.event.client.RequestClient;
import ru.practicum.event.client.UserClient;
import ru.practicum.event.dto.*;
import ru.practicum.event.enums.RequestStatus;
import ru.practicum.event.exception.ConflictException;
import ru.practicum.event.exception.NotFoundException;
import ru.practicum.event.exception.ValidationException;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.model.QEvent;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.client.CollectorGrpcClient;  // НОВЫЙ импорт
import ru.practicum.stats.proto.ActionTypeProto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final StatClient statClient;
    private final EventMapper eventMapper;
    private final CategoryClient categoryClient;
    private final UserClient userClient;
    private final RequestClient requestClient;
    private final EventDtoAssembler eventDtoAssembler;
    private final CollectorGrpcClient collectorGrpcClient;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    @Override
    public boolean isUserRegisteredOnEvent(Long userId, Long eventId) {
        try {
            // Проверяем через request-client, есть ли подтверждённая заявка
            List<ParticipationRequestDto> requests = requestClient.getEventRequests(userId, eventId);
            return requests.stream()
                    .anyMatch(r -> r.getRequesterId().equals(userId) &&
                            r.getStatus() == RequestStatus.CONFIRMED);
        } catch (Exception e) {
            log.error("Error checking user registration: userId={}, eventId={}", userId, eventId, e);
            return false;
        }
    }

    private void checkUser(Long userId) {
        if (userId == null) {
            throw new NotFoundException("User not found");
        }
        try {
            userClient.getUserById(userId);
        } catch (Exception e) {
            log.error("User not found: {}", userId, e);
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
    }

    private void checkCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ValidationException("Category ID cannot be null");
        }
        try {
            categoryClient.getCategoryById(categoryId);
        } catch (Exception e) {
            log.error("Category not found: {}", categoryId, e);
            throw new NotFoundException("Category with id=" + categoryId + " was not found");
        }
    }

    private Event getEventByIdOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    private void sendStat(HttpServletRequest request) {
        EndpointHitDto hit = new EndpointHitDto();
        hit.setApp("event-service");
        hit.setUri(request.getRequestURI());
        hit.setIp(request.getRemoteAddr());
        hit.setTimestamp(LocalDateTime.now().format(FORMATTER));
        statClient.hit(hit);
    }

    private String decodeUrl(String input) {
        if (input == null) return null;
        return input.replace("%20", " ").replace("%3A", ":");
    }

    private LocalDateTime parseTime(String time) {
        if (time == null) return null;
        String decoded = decodeUrl(time);
        return LocalDateTime.parse(decoded, FORMATTER);
    }

    // ========== GET METHODS ==========

    @Override
    public List<EventShortDto> getEventsByIds(List<Long> ids) {
        List<Event> events = eventRepository.findAllById(ids);
        return eventDtoAssembler.toShortDtoList(events);
    }

    @Override
    public List<EventFullDto> getEventsAdmin(EventAdminFilterParams params) {
        BooleanBuilder builder = new BooleanBuilder();
        QEvent qEvent = QEvent.event;

        if (params.getUsers() != null && !params.getUsers().isEmpty()) {
            builder.and(qEvent.initiatorId.in(params.getUsers()));
        }
        if (params.getStates() != null && !params.getStates().isEmpty()) {
            builder.and(qEvent.state.in(params.getStates().stream()
                    .map(EventState::valueOf)
                    .collect(Collectors.toList())));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            builder.and(qEvent.categoryId.in(params.getCategories()));
        }
        if (params.getRangeStart() != null) {
            builder.and(qEvent.eventDate.goe(parseTime(params.getRangeStart())));
        }
        if (params.getRangeEnd() != null) {
            builder.and(qEvent.eventDate.loe(parseTime(params.getRangeEnd())));
        }

        PageRequest pageRequest = PageRequest.of(params.getFrom() / params.getSize(), params.getSize());
        List<Event> events = eventRepository.findAll(builder, pageRequest).getContent();

        return eventDtoAssembler.toFullDtoList(events);
    }

    @Override
    public EventFullDto getEventPublic(Long id, HttpServletRequest request) {
        log.info("Getting public event with id: {}", id);

        Event event = getEventByIdOrThrow(id);

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event must be published");
        }

        // НОВОЕ: отправляем VIEW в Collector через gRPC
        String userIdHeader = request.getHeader("X-EWM-USER-ID");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                long userId = Long.parseLong(userIdHeader);
                collectorGrpcClient.sendUserAction(userId, id, ActionTypeProto.ACTION_VIEW);
                log.info("Sent VIEW action to Collector: userId={}, eventId={}", userId, id);
            } catch (NumberFormatException e) {
                log.warn("Invalid user ID header format: {}", userIdHeader);
            } catch (Exception e) {
                log.error("Failed to send VIEW action to Collector: userId={}, eventId={}", userIdHeader, id, e);
            }
        } else {
            log.debug("No user ID header found, skipping VIEW action sending");
        }

        // СТАРОЕ: отправляем статистику в stats-server (для обратной совместимости)
        sendStat(request);

        // Получаем дополнительные данные для DTO
        EventFullDto eventFullDto = eventDtoAssembler.toFullDto(event);

        // Получаем количество подтверждённых заявок
        try {
            Long confirmedRequests = requestClient.getConfirmedRequestsCount(event.getId());
            eventFullDto.setConfirmedRequests(confirmedRequests);
        } catch (Exception e) {
            log.warn("Failed to get confirmed requests count for eventId={}", event.getId(), e);
            eventFullDto.setConfirmedRequests(0L);
        }

        // Получаем количество просмотров (из старой статистики)
        try {
            Map<Long, Long> views = getViewsFromStats(List.of(event));
            Long viewsCount = views.getOrDefault(event.getId(), 0L);
            eventFullDto.setViews(viewsCount);
        } catch (Exception e) {
            log.warn("Failed to get views for eventId={}", event.getId(), e);
            eventFullDto.setViews(0L);
        }

        return eventFullDto;
    }

    private List<EventShortDto> getEventsSortedByViews(BooleanBuilder builder, EventPublicFilterParams params) {
        List<Event> events = new ArrayList<>();
        eventRepository.findAll(builder).forEach(events::add);

        Map<Long, Long> views = getViewsFromStats(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequestsFromClient(events);

        List<EventShortDto> result = new ArrayList<>();
        for (Event event : events) {
            EventShortDto dto = eventMapper.toShortDto(event);
            dto.setViews(views.getOrDefault(event.getId(), 0L));
            dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
            dto.setCategory(getCategoryFromClient(event.getCategoryId()));
            dto.setInitiator(getUserFromClient(event.getInitiatorId()));
            result.add(dto);
        }

        result.sort(Comparator.comparing(EventShortDto::getViews).reversed());

        int from = params.getFrom();
        int size = params.getSize();
        int toIndex = Math.min(from + size, result.size());

        return from >= result.size() ? Collections.emptyList() : result.subList(from, toIndex);
    }

    @Override
    public EventFullDto getEventPublic(Long id, HttpServletRequest request) {
        Event event = getEventByIdOrThrow(id);
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event must be published");
        }
        sendStat(request);
        return eventDtoAssembler.toFullDto(event);
    }

    @Override
    public List<EventShortDto> getEventsUser(Long userId, int from, int size) {
        checkUser(userId);
        PageRequest pageRequest = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageRequest);
        return eventDtoAssembler.toShortDtoList(events);
    }

    @Override
    public EventFullDto getEventUser(Long userId, Long eventId) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        return eventDtoAssembler.toFullDto(event);
    }

    @Override
    public EventFullDto getEventById(Long eventId) {
        Event event = getEventByIdOrThrow(eventId);
        return eventDtoAssembler.toFullDto(event);
    }

    @Override
    public List<EventShortDto> getEventsByCategoryId(Long categoryId) {
        List<Event> events = eventRepository.findAllByCategoryId(categoryId);
        return eventDtoAssembler.toShortDtoList(events);
    }

    // ========== CREATE METHODS ==========

    @Override
    @Transactional
    public EventFullDto createEventUser(Long userId, NewEventDto newEventDto) {
        log.info("Creating event for user {} with data: {}", userId, newEventDto);

        checkUser(userId);

        if (newEventDto.getCategoryId() == null) {
            throw new ValidationException("Category ID is required");
        }

        checkCategory(newEventDto.getCategoryId());

        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }

        Event event = eventMapper.toEntity(newEventDto);
        event.setInitiatorId(userId);
        event.setCategoryId(newEventDto.getCategoryId());
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        event.setLocation(eventMapper.toLocation(newEventDto.getLocation()));

        Event saved = eventRepository.save(event);
        log.info("Event created with id: {}", saved.getId());

        return eventDtoAssembler.toFullDto(saved);
    }

    // ========== UPDATE METHODS ==========

    @Override
    @Transactional
    public EventFullDto updateEventUser(Long userId, Long eventId, UpdateEventUserDto request) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        if (request.getEventDate() != null && request.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }

        updateEventFields(event, request);

        if (request.getStateAction() != null) {
            if (request.getStateAction() == UpdateEventUserDto.StateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (request.getStateAction() == UpdateEventUserDto.StateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        Event updated = eventRepository.save(event);
        return eventDtoAssembler.toFullDto(updated);
    }

    @Override
    @Transactional
    public EventFullDto updateEventAdmin(Long eventId, UpdateEventAdminDto request) {
        Event event = getEventByIdOrThrow(eventId);

        if (request.getEventDate() != null && request.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Event date must be at least 1 hour from now");
        }

        if (request.getStateAction() != null) {
            if (request.getStateAction() == UpdateEventAdminDto.StateAction.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (request.getStateAction() == UpdateEventAdminDto.StateAction.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject the event because it's already published");
                }
                event.setState(EventState.CANCELED);
            }
        }

        updateEventFields(event, request);

        Event updated = eventRepository.save(event);
        return eventDtoAssembler.toFullDto(updated);
    }

    private void updateEventFields(Event event, BaseUpdateEventDto request) {
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getAnnotation() != null) event.setAnnotation(request.getAnnotation());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getEventDate() != null) event.setEventDate(request.getEventDate());
        if (request.getCategoryId() != null) event.setCategoryId(request.getCategoryId());
        if (request.getLocation() != null) event.setLocation(eventMapper.toLocation(request.getLocation()));
        if (request.getPaid() != null) event.setPaid(request.getPaid());
        if (request.getParticipantLimit() != null) event.setParticipantLimit(request.getParticipantLimit());
        if (request.getRequestModeration() != null) event.setRequestModeration(request.getRequestModeration());
    }

    // ========== REQUEST METHODS ==========

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        checkUser(userId);
        return requestClient.getEventRequests(userId, eventId);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId, EventRequestStatusUpdateDto request) {
        log.info("Changing request status: userId={}, eventId={}, request={}", userId, eventId, request);
        checkUser(userId);
        try {
            return requestClient.changeRequestStatus(userId, eventId, request);
        } catch (FeignException e) {
            log.error("Feign error while changing request status: status={}, message={}", e.status(), e.getMessage());
            if (e.status() == 409) {
                throw new ConflictException(e.getMessage());
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Creating request: userId={}, eventId={}", userId, eventId);
        getEventByIdOrThrow(eventId);
        try {
            return requestClient.createRequest(userId, eventId);
        } catch (FeignException e) {
            log.error("Feign error while creating request: status={}, message={}", e.status(), e.getMessage());
            if (e.status() == 409) throw new ConflictException(e.getMessage());
            if (e.status() == 400) throw new ValidationException(e.getMessage());
            throw e;
        }
    }

    @Override
    public List<ParticipationRequestDto> getRequestsByUser(Long userId) {
        log.info("Getting requests for user: {}", userId);
        checkUser(userId);
        return requestClient.getRequestsByUser(userId);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Cancelling request: userId={}, requestId={}", userId, requestId);
        checkUser(userId);
        try {
            return requestClient.cancelRequest(userId, requestId);
        } catch (FeignException e) {
            log.error("Feign error while cancelling request: status={}, message={}", e.status(), e.getMessage());
            if (e.status() == 409) throw new ConflictException(e.getMessage());
            if (e.status() == 404) throw new NotFoundException(e.getMessage());
            throw e;
        }
    }

    // ========== ПРИВАТНЫЕ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ PUBLIC ENDPOINT ==========

    private LocalDateTime parseStartDate(String rangeStart) {
        if (rangeStart == null || rangeStart.isBlank()) {
            return LocalDateTime.now();
        }
        return LocalDateTime.parse(decodeUrl(rangeStart), FORMATTER);
    }

    private LocalDateTime parseEndDate(String rangeEnd) {
        if (rangeEnd == null || rangeEnd.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(decodeUrl(rangeEnd), FORMATTER);
    }

    private void validateDates(LocalDateTime start, LocalDateTime end, String rangeStart, String rangeEnd) {
        if (rangeStart != null && rangeEnd != null && start != null && end != null && start.isAfter(end)) {
            throw new ValidationException("Start date must be before end date");
        }
    }

    private List<Long> validateCategories(List<Long> categories) {
        List<Long> validCategories = new ArrayList<>();
        for (Long catId : categories) {
            if (catId == null || catId <= 0) continue;
            try {
                categoryClient.getCategoryById(catId);
                validCategories.add(catId);
            } catch (Exception e) {
                log.warn("Category {} not found, skipping", catId);
            }
        }
        return validCategories;
    }

    private Map<Long, Long> getViewsFromStats(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> views = new HashMap<>();
        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());

        try {
            List<ViewStatsDto> stats = statClient.getStat(
                    LocalDateTime.now().minusYears(100),
                    LocalDateTime.now().plusYears(100),
                    uris, true);
            for (ViewStatsDto stat : stats) {
                String[] parts = stat.getUri().split("/");
                if (parts.length >= 3) {
                    views.put(Long.parseLong(parts[2]), stat.getHits());
                }
            }
        } catch (Exception e) {
            log.error("Error getting stats", e);
        }
        return views;
    }

    private Map<Long, Long> getConfirmedRequestsFromClient(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        try {
            return requestClient.getConfirmedRequestsCounts(eventIds);
        } catch (Exception e) {
            log.error("Error getting confirmed requests counts", e);
            return Collections.emptyMap();
        }
    }

    private CategoryDto getCategoryFromClient(Long categoryId) {
        if (categoryId == null) {
            CategoryDto defaultCategory = new CategoryDto();
            defaultCategory.setId(0L);
            defaultCategory.setName("Unknown Category");
            return defaultCategory;
        }
        try {
            return categoryClient.getCategoryById(categoryId);
        } catch (Exception e) {
            CategoryDto defaultCategory = new CategoryDto();
            defaultCategory.setId(categoryId);
            defaultCategory.setName("Unknown Category");
            return defaultCategory;
        }
    }

    private UserShortDto getUserFromClient(Long userId) {
        if (userId == null) return null;
        try {
            UserDto user = userClient.getUserById(userId);
            return new UserShortDto(user.getId(), user.getName());
        } catch (Exception e) {
            UserShortDto defaultUser = new UserShortDto();
            defaultUser.setId(userId);
            defaultUser.setName("Unknown User");
            return defaultUser;
        }
    }
}