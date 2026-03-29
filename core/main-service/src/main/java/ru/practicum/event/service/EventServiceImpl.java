package ru.practicum.event.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatClient;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;
import ru.practicum.event.model.Location;
import ru.practicum.event.model.QEvent;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.mapper.ParticipationRequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.model.QParticipationRequest;
import ru.practicum.request.repository.ConfirmedRequestView;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.user.model.User;
import ru.practicum.user.repository.UserRepository;

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
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatClient statClient;
    private final EventMapper eventMapper;
    private final ParticipationRequestMapper requestMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<EventFullDto> getEventsAdmin(EventAdminFilterParams params) {
        BooleanBuilder builder = new BooleanBuilder();
        QEvent qEvent = QEvent.event;

        if (params.getUsers() != null && !params.getUsers().isEmpty()) {
            builder.and(qEvent.initiator.id.in(params.getUsers()));
        }
        if (params.getStates() != null && !params.getStates().isEmpty()) {
            builder.and(qEvent.state.in(params.getStates().stream()
                    .map(EventState::valueOf)
                    .collect(Collectors.toList())));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            builder.and(qEvent.category.id.in(params.getCategories()));
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

        BooleanBuilder builder = new BooleanBuilder();
        QEvent qEvent = QEvent.event;

        builder.and(qEvent.state.eq(EventState.PUBLISHED));

        if (params.getText() != null && !params.getText().isBlank()) {
            builder.and(qEvent.annotation.containsIgnoreCase(params.getText())
                    .or(qEvent.description.containsIgnoreCase(params.getText())));
        }
        if (params.getCategories() != null && !params.getCategories().isEmpty()) {
            builder.and(qEvent.category.id.in(params.getCategories()));
        }
        if (params.getPaid() != null) {
            builder.and(qEvent.paid.eq(params.getPaid()));
        }

        LocalDateTime start = (params.getRangeStart() != null) ? parseTime(params.getRangeStart()) : LocalDateTime.now();
        LocalDateTime end = (params.getRangeEnd() != null) ? parseTime(params.getRangeEnd()) : null;

        if (end != null && start.isAfter(end)) {
            throw new ValidationException("Start date must be before end date");
        }

        builder.and(qEvent.eventDate.goe(start));
        if (end != null) {
            builder.and(qEvent.eventDate.loe(end));
        }

        if (Boolean.TRUE.equals(params.getOnlyAvailable())) {
            builder.and(qEvent.participantLimit.eq(0)
                    .or(qEvent.participantLimit.gt(
                            JPAExpressions.select(QParticipationRequest.participationRequest.count())
                                    .from(QParticipationRequest.participationRequest)
                                    .where(QParticipationRequest.participationRequest.event.id.eq(qEvent.id)
                                            .and(QParticipationRequest.participationRequest.status.eq(RequestStatus.CONFIRMED)))
                    )));
        }

        Sort sortOrder = Sort.by(Sort.Direction.ASC, "eventDate");

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

        PageRequest pageRequest = PageRequest.of(params.getFrom() / params.getSize(), params.getSize(), sortOrder);
        List<Event> events = eventRepository.findAll(builder, pageRequest).getContent();

        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);
        List<EventShortDto> result = new ArrayList<>();

        for (Event event : events) {
            EventShortDto dto = eventMapper.toShortDto(event);
            dto.setViews(views.getOrDefault(event.getId(), 0L));
            dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
            result.add(dto);
        }

        return result;
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
        User user = checkUser(userId);

        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }

        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category not found"));

        Event event = eventMapper.toEntity(newEventDto);
        event.setInitiator(user);
        event.setCategory(category);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());

        Location location = eventMapper.toLocation(newEventDto.getLocation());
        event.setLocation(location);

        return toEventFullDtoWithStats(eventRepository.save(event));
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
    public List<ru.practicum.request.dto.ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        checkUser(userId);
        eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        return requestRepository.findAllByEventId(eventId).stream()
                .map(requestMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId, EventRequestStatusUpdateDto request) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            throw new ConflictException("Moderation is not required");
        }

        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() <= confirmedCount) {
             throw new ConflictException("The participant limit has been reached");
        }

        List<ParticipationRequest> requests = requestRepository.findAllByIdIn(request.getRequestIds());
        List<ru.practicum.request.dto.ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ru.practicum.request.dto.ParticipationRequestDto> rejected = new ArrayList<>();

        for (ParticipationRequest req : requests) {
            if (req.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }

            if (request.getStatus() == RequestStatus.CONFIRMED) {
                if (confirmedCount < event.getParticipantLimit()) {
                    req.setStatus(RequestStatus.CONFIRMED);
                    confirmed.add(requestMapper.toDto(req));
                    confirmedCount++;
                } else {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(requestMapper.toDto(req));
                }
            } else {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(requestMapper.toDto(req));
            }
        }
        requestRepository.saveAll(requests);
        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }

    private Event updateEvent(Event event, BaseUpdateEventDto request) {
        if (request.getTitle() != null) event.setTitle(request.getTitle());
        if (request.getAnnotation() != null) event.setAnnotation(request.getAnnotation());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getEventDate() != null) event.setEventDate(request.getEventDate());
        if (request.getCategory() != null) {
            Category category = categoryRepository.findById(request.getCategory())
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            event.setCategory(category);
        }
        if (request.getLocation() != null) {
            event.setLocation(eventMapper.toLocation(request.getLocation()));
        }
        if (request.getPaid() != null) event.setPaid(request.getPaid());
        if (request.getParticipantLimit() != null) event.setParticipantLimit(request.getParticipantLimit());
        if (request.getRequestModeration() != null) event.setRequestModeration(request.getRequestModeration());

        return eventRepository.save(event);
    }

    private User checkUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private LocalDateTime parseTime(String time) {
        return LocalDateTime.parse(time, FORMATTER);
    }

    private void sendStat(HttpServletRequest request) {
        EndpointHitDto hit = new EndpointHitDto();
        hit.setApp("ewm-main-service");
        hit.setUri(request.getRequestURI());
        hit.setIp(request.getRemoteAddr());
        hit.setTimestamp(LocalDateTime.now().format(FORMATTER));
        statClient.hit(hit);
    }

    private List<EventFullDto> makeEventFullDtoList(List<Event> events) {
        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventFullDto dto = eventMapper.toFullDto(event);
                    dto.setViews(views.getOrDefault(event.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<EventShortDto> makeEventShortDtoList(List<Event> events) {
        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    dto.setViews(views.getOrDefault(event.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
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

    private Map<Long, Long> getConfirmedRequests(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        List<ConfirmedRequestView> confirmedRequests = requestRepository.countByEventIdInAndStatus(eventIds, RequestStatus.CONFIRMED);

        return confirmedRequests.stream()
                .collect(Collectors.toMap(ConfirmedRequestView::getEventId, ConfirmedRequestView::getCount));
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
}