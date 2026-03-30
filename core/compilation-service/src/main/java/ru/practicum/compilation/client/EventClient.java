package ru.practicum.compilation.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.compilation.dto.EventShortDto;

import java.util.List;

@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/events/{eventId}")
    EventShortDto getEventById(@PathVariable("eventId") Long eventId);

    @GetMapping("/events")
    List<EventShortDto> getEventsByIds(@RequestParam("ids") List<Long> ids);
}