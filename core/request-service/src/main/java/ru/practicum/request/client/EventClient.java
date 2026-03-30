package ru.practicum.request.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.request.dto.EventFullDto;

@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/admin/events/{eventId}")
    EventFullDto getEventById(@PathVariable("eventId") Long eventId);
}