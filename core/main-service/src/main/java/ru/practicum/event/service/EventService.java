package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.event.dto.*;

import java.util.List;

public interface EventService {
    List<EventFullDto> getEventsAdmin(EventAdminFilterParams params);

    EventFullDto updateEventAdmin(Long eventId, UpdateEventAdminDto request);

    List<EventShortDto> getEventsPublic(EventPublicFilterParams params);

    EventFullDto getEventPublic(Long id, HttpServletRequest request);

    List<EventShortDto> getEventsUser(Long userId, int from, int size);

    EventFullDto createEventUser(Long userId, NewEventDto newEventDto);

    EventFullDto getEventUser(Long userId, Long eventId);

    EventFullDto updateEventUser(Long userId, Long eventId, UpdateEventUserDto request);

    List<ru.practicum.request.dto.ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId, EventRequestStatusUpdateDto request);

    EventFullDto getEventById(Long eventId);
}
