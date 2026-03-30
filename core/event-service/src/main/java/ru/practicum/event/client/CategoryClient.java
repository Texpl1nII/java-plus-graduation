package ru.practicum.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.practicum.event.dto.CategoryDto;

@FeignClient(name = "category-service")
public interface CategoryClient {

    @GetMapping("/categories/{catId}")
    CategoryDto getCategoryById(@PathVariable("catId") Long catId);
}