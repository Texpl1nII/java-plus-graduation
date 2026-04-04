package ru.practicum.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.EventRequestStatusUpdateDto;
import ru.practicum.event.dto.EventRequestStatusUpdateResult;
import ru.practicum.event.dto.ParticipationRequestDto;

import java.util.List;
import java.util.Map;

@FeignClient(name = "request-service")
public interface RequestClient {

    @GetMapping("/admin/events/{eventId}/requests/count")
    Long getConfirmedRequestsCount(@PathVariable("eventId") Long eventId);

    // Batch-метод для получения количества подтверждённых заявок для нескольких событий
    @GetMapping("/admin/events/requests/counts")
    Map<Long, Long> getConfirmedRequestsCounts(@RequestParam("eventIds") List<Long> eventIds);

    @GetMapping("/users/{userId}/events/{eventId}/requests")
    List<ParticipationRequestDto> getEventRequests(@PathVariable("userId") Long userId,
                                                   @PathVariable("eventId") Long eventId);

    @PostMapping("/users/{userId}/events/{eventId}/requests")
    EventRequestStatusUpdateResult changeRequestStatus(@PathVariable("userId") Long userId,
                                                       @PathVariable("eventId") Long eventId,
                                                       @RequestBody EventRequestStatusUpdateDto request);
}
