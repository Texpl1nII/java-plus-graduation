package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.event.dto.*;

import java.util.List;

public interface EventService {

    List<EventShortDto> getEventsByIds(List<Long> ids);

    List<EventFullDto> getEventsAdmin(EventAdminFilterParams params);

    EventFullDto updateEventAdmin(Long eventId, UpdateEventAdminDto request);

    EventFullDto getEventPublic(Long id, HttpServletRequest request);

    List<EventShortDto> getEventsUser(Long userId, int from, int size);

    EventFullDto createEventUser(Long userId, NewEventDto newEventDto);

    EventFullDto getEventUser(Long userId, Long eventId);

    EventFullDto updateEventUser(Long userId, Long eventId, UpdateEventUserDto request);

    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId, EventRequestStatusUpdateDto request);

    EventFullDto getEventById(Long eventId);

    List<EventShortDto> getEventsByCategoryId(Long categoryId);

    ParticipationRequestDto createRequest(Long userId, Long eventId);

    List<ParticipationRequestDto> getRequestsByUser(Long userId);

    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    boolean isUserRegisteredOnEvent(Long userId, Long eventId);

    List<EventShortDto> getEventsPublic(EventPublicFilterParams params);
}