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
import ru.practicum.event.dto.CategoryDto;
import java.util.Collections;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.client.CategoryClient;
import ru.practicum.event.client.RequestClient;
import ru.practicum.event.client.UserClient;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.model.Location;
import ru.practicum.event.model.QEvent;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.exception.ConflictException;
import ru.practicum.event.exception.NotFoundException;
import ru.practicum.event.exception.ValidationException;
import java.util.Comparator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        return requestClient.cancelRequest(userId, requestId);
    }

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Creating request: userId={}, eventId={}", userId, eventId);
        return requestClient.createRequest(userId, eventId);
    }

    @Override
    public List<EventShortDto> getEventsByIds(List<Long> ids) {
        List<Event> events = eventRepository.findAllById(ids);
        return makeEventShortDtoList(events);
    }

    private void checkCategory(Long categoryId) {
        if (categoryId == null) {
            throw new ValidationException("Category ID cannot be null");
        }
        log.info("Checking category with id: {}", categoryId);
        try {
            CategoryDto category = categoryClient.getCategoryById(categoryId);
            log.info("Category found: {}", category);
        } catch (Exception e) {
            log.error("Category not found: {}", categoryId, e);
            throw new NotFoundException("Category with id=" + categoryId + " was not found");
        }
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

        return makeEventFullDtoList(events);
    }

    @Override
    @Transactional
    public EventFullDto updateEventAdmin(Long eventId, UpdateEventAdminDto request) {
        Event event = getEventByIdOrThrow(eventId);

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ValidationException("Event date must be at least 1 hour from now");
            }
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

        return toEventFullDtoWithStats(updateEvent(event, request));
    }

    @Override
    public List<EventShortDto> getEventsPublic(EventPublicFilterParams params) {

        sendStat(params.getRequest());

        // ========== 1. СНАЧАЛА ПАРСИНГ И ВАЛИДАЦИЯ ДАТ ==========
        log.info("=== DATE PARSING ===");
        log.info("Raw rangeStart: '{}'", params.getRangeStart());
        log.info("Raw rangeEnd: '{}'", params.getRangeEnd());

        LocalDateTime start = null;
        LocalDateTime end = null;

        try {
            if (params.getRangeStart() != null && !params.getRangeStart().isBlank()) {
                String decodedStart = decodeUrl(params.getRangeStart());
                log.info("Decoded rangeStart: '{}'", decodedStart);
                start = LocalDateTime.parse(decodedStart, FORMATTER);
            } else {
                start = LocalDateTime.now();
            }

            if (params.getRangeEnd() != null && !params.getRangeEnd().isBlank()) {
                String decodedEnd = decodeUrl(params.getRangeEnd());
                log.info("Decoded rangeEnd: '{}'", decodedEnd);
                end = LocalDateTime.parse(decodedEnd, FORMATTER);
            }
        } catch (Exception e) {
            log.error("Failed to parse dates: {}", e.getMessage(), e);
            throw new ValidationException("Invalid date format. Expected: yyyy-MM-dd HH:mm:ss");
        }

        log.info("Parsed dates: start={}, end={}", start, end);

        // ВАЛИДАЦИЯ: start должен быть раньше end (если оба указаны)
        if (params.getRangeStart() != null && params.getRangeEnd() != null) {
            if (start != null && end != null && start.isAfter(end)) {
                log.warn("Validation failed: start={} is after end={}", start, end);
                throw new ValidationException("Start date must be before end date");
            }
        }
        // ========================================================

        // ========== 2. ЛОГИРОВАНИЕ ВСЕХ ПАРАМЕТРОВ ==========
        log.info("=== getEventsPublic START ===");
        log.info("text={}", params.getText());
        log.info("categories={}", params.getCategories());
        log.info("paid={}", params.getPaid());
        log.info("rangeStart={}", params.getRangeStart());
        log.info("rangeEnd={}", params.getRangeEnd());
        log.info("onlyAvailable={}", params.getOnlyAvailable());
        log.info("sort={}", params.getSort());
        log.info("from={}, size={}", params.getFrom(), params.getSize());
        // ========================================================

        BooleanBuilder builder = new BooleanBuilder();
        QEvent qEvent = QEvent.event;

        builder.and(qEvent.state.eq(EventState.PUBLISHED));

        if (params.getText() != null && !params.getText().isBlank()) {
            builder.and(qEvent.annotation.containsIgnoreCase(params.getText())
                    .or(qEvent.description.containsIgnoreCase(params.getText())));
        }

        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            List<Long> validCategories = new ArrayList<>();

            for (Long catId : params.getCategories()) {
                if (catId == null || catId <= 0) {
                    log.warn("Skipping invalid category id: {}", catId);
                    continue;
                }

                try {
                    CategoryDto category = categoryClient.getCategoryById(catId);
                    if (category != null && category.getId() != null) {
                        validCategories.add(catId);
                        log.debug("Category {} is valid, adding to filter", catId);
                    }
                } catch (FeignException.NotFound e) {
                    log.warn("Category {} not found, skipping", catId);
                } catch (Exception e) {
                    log.warn("Error checking category {}: {}", catId, e.getMessage());
                }
            }

            if (validCategories.isEmpty()) {
                log.info("No valid categories found, returning empty list");
                return Collections.emptyList();
            }

            builder.and(qEvent.categoryId.in(validCategories));
            log.info("Filtering by valid categories: {}", validCategories);
        }

        if (params.getPaid() != null) {
            builder.and(qEvent.paid.eq(params.getPaid()));
        }

        builder.and(qEvent.eventDate.goe(start));
        if (end != null) {
            builder.and(qEvent.eventDate.loe(end));
        }

        if (Boolean.TRUE.equals(params.getOnlyAvailable())) {
            builder.and(qEvent.participantLimit.eq(0));
        }

        Sort sortOrder = Sort.by(Sort.Direction.ASC, "eventDate");

        // ========== ВЕТКА СОРТИРОВКИ ПО VIEWS (ИСПРАВЛЕНА) ==========
        if ("VIEWS".equals(params.getSort())) {
            List<Event> events = StreamSupport.stream(eventRepository.findAll(builder).spliterator(), false)
                    .collect(Collectors.toList());

            Map<Long, Long> views = getViews(events);
            Map<Long, Long> confirmedRequests = getConfirmedRequests(events);
            List<EventShortDto> result = new ArrayList<>();

            for (Event event : events) {
                EventShortDto dto = eventMapper.toShortDto(event);
                dto.setViews(views.getOrDefault(event.getId(), 0L));
                dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));

                Long categoryId = event.getCategoryId();
                CategoryDto category;
                if (categoryId == null) {
                    log.warn("Event {} has null categoryId", event.getId());
                    category = new CategoryDto();
                    category.setId(0L);
                    category.setName("Unknown Category");
                } else {
                    category = getCategoryById(categoryId);
                    if (category == null) {
                        category = new CategoryDto();
                        category.setId(categoryId);
                        category.setName("Unknown Category");
                    }
                }
                dto.setCategory(category);

                Long initiatorId = event.getInitiatorId();
                UserShortDto initiator;
                if (initiatorId == null) {
                    log.warn("Event {} has null initiatorId", event.getId());
                    initiator = new UserShortDto();
                    initiator.setId(0L);
                    initiator.setName("Unknown User");
                } else {
                    initiator = getUserById(initiatorId);
                    if (initiator == null) {
                        initiator = new UserShortDto();
                        initiator.setId(initiatorId);
                        initiator.setName("Unknown User");
                    }
                }
                dto.setInitiator(initiator);

                result.add(dto);
            }

            result.sort(Comparator.comparing(EventShortDto::getViews).reversed());

            int from = params.getFrom();
            int size = params.getSize();
            int toIndex = Math.min(from + size, result.size());

            if (from >= result.size()) {
                return Collections.emptyList();
            }

            return result.subList(from, toIndex);
        }
        // =======================================================

        PageRequest pageRequest = PageRequest.of(params.getFrom() / params.getSize(), params.getSize(), sortOrder);
        List<Event> events = eventRepository.findAll(builder, pageRequest).getContent();

        return makeEventShortDtoList(events);
    }

    @Override
    public EventFullDto getEventPublic(Long id, HttpServletRequest request) {
        Event event = getEventByIdOrThrow(id);

        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event must be published");
        }

        sendStat(request);
        return toEventFullDtoWithStats(event);
    }

    @Override
    public List<EventShortDto> getEventsUser(Long userId, int from, int size) {
        checkUser(userId);
        PageRequest pageRequest = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageRequest);
        return makeEventShortDtoList(events);
    }

    @Override
    @Transactional
    public EventFullDto createEventUser(Long userId, NewEventDto newEventDto) {
        log.info("Creating event for user {} with data: {}", userId, newEventDto);

        checkUser(userId);

        // Проверка обязательности categoryId (убрали fallback)
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

        Location location = eventMapper.toLocation(newEventDto.getLocation());
        event.setLocation(location);

        Event saved = eventRepository.save(event);
        log.info("Event created with id: {}", saved.getId());

        return toEventFullDtoWithStats(saved);
    }

    @Override
    public EventFullDto getEventUser(Long userId, Long eventId) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        return toEventFullDtoWithStats(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventUser(Long userId, Long eventId, UpdateEventUserDto request) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ValidationException("Event date must be at least 2 hours from now");
            }
        }

        if (request.getStateAction() != null) {
            if (request.getStateAction() == UpdateEventUserDto.StateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (request.getStateAction() == UpdateEventUserDto.StateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }

        return toEventFullDtoWithStats(updateEvent(event, request));
    }

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
        } catch (feign.FeignException e) {
            log.error("Feign error while changing request status: status={}, message={}", e.status(), e.getMessage());
            if (e.status() == 409) {
                throw new ConflictException(e.getMessage());
            }
            throw e;
        }
    }

    private Event updateEvent(Event event, BaseUpdateEventDto request) {
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getAnnotation() != null) event.setAnnotation(request.getAnnotation());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getEventDate() != null) event.setEventDate(request.getEventDate());
        if (request.getCategoryId() != null) {
            event.setCategoryId(request.getCategoryId());
        }
        if (request.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(request.getLocation()));
        }
        if (request.getPaid() != null) event.setPaid(request.getPaid());
        if (request.getParticipantLimit() != null) event.setParticipantLimit(request.getParticipantLimit());
        if (request.getRequestModeration() != null) event.setRequestModeration(request.getRequestModeration());

        return eventRepository.save(event);
    }

    private LocalDateTime parseTime(String time) {
        if (time == null) return null;
        String decoded = decodeUrl(time);
        return LocalDateTime.parse(decoded, FORMATTER);
    }

    private void sendStat(HttpServletRequest request) {
        EndpointHitDto hit = new EndpointHitDto();
        hit.setApp("event-service");
        hit.setUri(request.getRequestURI());
        hit.setIp(request.getRemoteAddr());
        hit.setTimestamp(LocalDateTime.now().format(FORMATTER));
        statClient.hit(hit);
    }

    private List<EventFullDto> makeEventFullDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch загрузка категорий
        List<Long> allCategoryIds = events.stream()
                .map(Event::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, CategoryDto> categoriesMap = getCategoriesBatch(allCategoryIds);

        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventFullDto dto = eventMapper.toFullDto(event);

                    dto.setViews(views.getOrDefault(event.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));

                    CategoryDto category = categoriesMap.get(event.getCategoryId());
                    if (category == null) {
                        category = new CategoryDto();
                        category.setId(event.getCategoryId());
                        category.setName("Unknown Category");
                    }
                    dto.setCategory(category);

                    UserShortDto initiator = getUserById(event.getInitiatorId());
                    if (initiator == null) {
                        initiator = new UserShortDto();
                        initiator.setId(event.getInitiatorId());
                        initiator.setName("Unknown User");
                    }
                    dto.setInitiator(initiator);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<EventShortDto> makeEventShortDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        // Batch загрузка категорий
        List<Long> allCategoryIds = events.stream()
                .map(Event::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, CategoryDto> categoriesMap = getCategoriesBatch(allCategoryIds);

        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);

                    dto.setViews(views.getOrDefault(event.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));

                    CategoryDto category = categoriesMap.get(event.getCategoryId());
                    if (category == null) {
                        category = new CategoryDto();
                        category.setId(event.getCategoryId());
                        category.setName("Unknown Category");
                    }
                    dto.setCategory(category);

                    UserShortDto initiator = getUserById(event.getInitiatorId());
                    if (initiator == null) {
                        initiator = new UserShortDto();
                        initiator.setId(event.getInitiatorId());
                        initiator.setName("Unknown User");
                    }
                    dto.setInitiator(initiator);

                    return dto;
                })
                .collect(Collectors.toList());
    }

    private EventFullDto toEventFullDtoWithStats(Event event) {
        return makeEventFullDtoList(List.of(event)).get(0);
    }

    private Map<Long, Long> getViews(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> views = new HashMap<>();
        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());

        try {
            List<ViewStatsDto> stats = statClient.getStat(LocalDateTime.now().minusYears(100), LocalDateTime.now().plusYears(100), uris, true);
            for (ViewStatsDto stat : stats) {
                String[] parts = stat.getUri().split("/");
                if (parts.length >= 3) {
                    Long eventId = Long.parseLong(parts[2]);
                    views.put(eventId, stat.getHits());
                }
            }
        } catch (Exception e) {
            log.error("Error getting stats", e);
        }
        return views;
    }

    // Исправленный метод с batch-запросом
    private Map<Long, Long> getConfirmedRequests(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        try {
            return requestClient.getConfirmedRequestsCounts(eventIds);
        } catch (Exception e) {
            log.error("Error getting confirmed requests counts for events {}", eventIds, e);
            return Collections.emptyMap();
        }
    }

    @Override
    public EventFullDto getEventById(Long eventId) {
        Event event = getEventByIdOrThrow(eventId);
        return toEventFullDtoWithStats(event);
    }

    private Event getEventByIdOrThrow(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
    }

    // Batch метод для получения категорий
    private Map<Long, CategoryDto> getCategoriesBatch(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = categoryIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            // ✅ ИСПРАВЛЕНО: получаем List, преобразуем в Map
            List<CategoryDto> categories = categoryClient.getCategoriesByIds(distinctIds);
            return categories.stream()
                    .collect(Collectors.toMap(
                            CategoryDto::getId,
                            category -> category,
                            (existing, replacement) -> existing
                    ));
        } catch (Exception e) {
            log.error("Failed to get categories batch: {}", distinctIds, e);
            return Collections.emptyMap();
        }
    }

    private CategoryDto getCategoryById(Long categoryId) {
        if (categoryId == null) {
            log.warn("Category ID is null, returning default category");
            CategoryDto defaultCategory = new CategoryDto();
            defaultCategory.setId(0L);
            defaultCategory.setName("Unknown Category");
            return defaultCategory;
        }

        try {
            return categoryClient.getCategoryById(categoryId);
        } catch (FeignException.NotFound e) {
            log.warn("Category with id {} not found", categoryId);
            CategoryDto defaultCategory = new CategoryDto();
            defaultCategory.setId(categoryId);
            defaultCategory.setName("Unknown Category");
            return defaultCategory;
        } catch (Exception e) {
            log.error("Failed to get category with id: {}", categoryId, e);
            CategoryDto defaultCategory = new CategoryDto();
            defaultCategory.setId(categoryId);
            defaultCategory.setName("Unknown Category");
            return defaultCategory;
        }
    }

    private UserShortDto getUserById(Long userId) {
        if (userId == null) return null;
        try {
            UserDto user = userClient.getUserById(userId);
            return new UserShortDto(user.getId(), user.getName());
        } catch (FeignException.NotFound e) {
            log.warn("User with id {} not found", userId);
            UserShortDto defaultUser = new UserShortDto();
            defaultUser.setId(userId);
            defaultUser.setName("Unknown User");
            return defaultUser;
        } catch (Exception e) {
            log.error("Failed to get user with id: {}", userId, e);
            UserShortDto defaultUser = new UserShortDto();
            defaultUser.setId(userId);
            defaultUser.setName("Unknown User");
            return defaultUser;
        }
    }

    @Override
    public List<EventShortDto> getEventsByCategoryId(Long categoryId) {
        log.info("Getting events by category id: {}", categoryId);
        List<Event> events = eventRepository.findAllByCategoryId(categoryId);
        return makeEventShortDtoList(events);
    }

    private String decodeUrl(String input) {
        if (input == null) return null;
        return input.replace("%20", " ").replace("%3A", ":");
    }
}