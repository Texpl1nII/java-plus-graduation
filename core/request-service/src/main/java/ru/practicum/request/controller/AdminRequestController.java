package ru.practicum.request.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.request.service.ParticipationRequestService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/events")
@RequiredArgsConstructor
@Slf4j
public class AdminRequestController {

    private final ParticipationRequestService requestService;

    @GetMapping("/requests/counts")
    public Map<Long, Long> getConfirmedRequestsCounts(@RequestParam List<Long> eventIds) {
        log.info("Get confirmed requests counts for events: {}", eventIds);
        return requestService.getConfirmedRequestsCounts(eventIds);
    }
}
