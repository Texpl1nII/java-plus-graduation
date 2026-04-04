package ru.practicum.category.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.category.dto.EventShortDto;

import java.util.List;

@FeignClient(name = "event-service")
public interface EventClient {

    @GetMapping("/admin/events/by-category/{categoryId}")
    List<EventShortDto> getEventsByCategoryId(@PathVariable("categoryId") Long categoryId);
}