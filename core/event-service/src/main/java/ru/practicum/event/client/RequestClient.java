package ru.practicum.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.EventRequestStatusUpdateDto;
import ru.practicum.event.dto.EventRequestStatusUpdateResult;
import ru.practicum.event.dto.ParticipationRequestDto;

import java.util.List;

@FeignClient(name = "request-service")
public interface RequestClient {

    @GetMapping("/admin/events/{eventId}/requests/count")
    Long getConfirmedRequestsCount(@PathVariable("eventId") Long eventId);

    @GetMapping("/users/{userId}/events/{eventId}/requests")
    List<ParticipationRequestDto> getEventRequests(@PathVariable("userId") Long userId,
                                                   @PathVariable("eventId") Long eventId);

    @PatchMapping("/users/{userId}/events/{eventId}/requests")
    EventRequestStatusUpdateResult changeRequestStatus(@PathVariable("userId") Long userId,
                                                       @PathVariable("eventId") Long eventId,
                                                       @RequestBody EventRequestStatusUpdateDto request);
}
