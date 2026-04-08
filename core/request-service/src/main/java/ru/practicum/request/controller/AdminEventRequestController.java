package ru.practicum.request.controller;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.service.ParticipationRequestService;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/admin/events")
@Validated
public class AdminEventRequestController {

    private final ParticipationRequestService requestService;

    @GetMapping("/{eventId}/requests/count")
    public Long getRequestsCount(@PathVariable @Positive Long eventId) {
        log.info("GET: Получение количества запросов для события eventId={}", eventId);
        return requestService.countByEventId(eventId);
    }
}
