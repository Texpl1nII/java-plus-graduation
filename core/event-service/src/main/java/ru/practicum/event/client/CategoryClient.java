package ru.practicum.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.dto.CategoryDto;

import java.util.List;

@FeignClient(name = "category-service")
public interface CategoryClient {

    @GetMapping("/categories/{catId}")
    CategoryDto getCategoryById(@PathVariable("catId") Long catId);

    @GetMapping("/categories")
    List<CategoryDto> getAllCategories(@RequestParam("from") int from,
                                       @RequestParam("size") int size);

    // ✅ ИСПРАВЛЕНО: List вместо Map
    @GetMapping("/categories/batch")
    List<CategoryDto> getCategoriesByIds(@RequestParam("ids") List<Long> ids);
}