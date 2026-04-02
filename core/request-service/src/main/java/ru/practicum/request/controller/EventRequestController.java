package ru.practicum.request.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.dto.EventRequestStatusUpdateDto;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.ParticipationRequestService;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/users/{userId}/events/{eventId}/requests")
@Validated
public class EventRequestController {

    private final ParticipationRequestService requestService;

    @GetMapping
    public List<ParticipationRequestDto> getEventRequests(@PathVariable @Positive Long userId,
                                                          @PathVariable @Positive Long eventId) {
        log.info("GET: Получение запросов на участие в событии userId={}, eventId={}", userId, eventId);
        return requestService.getEventRequests(userId, eventId);
    }

    @PostMapping
    public EventRequestStatusUpdateResult changeRequestStatus(@PathVariable @Positive Long userId,
                                                              @PathVariable @Positive Long eventId,
                                                              @Valid @RequestBody EventRequestStatusUpdateDto updateDto) {
        log.info("POST: Изменение статуса заявок userId={}, eventId={}, updateDto={}", userId, eventId, updateDto);
        return requestService.changeRequestStatus(userId, eventId, updateDto);
    }
}
