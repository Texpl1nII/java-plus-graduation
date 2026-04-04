package ru.practicum.event.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import ru.practicum.event.dto.CategoryDto;

import java.util.List;
import java.util.Map;

@FeignClient(name = "category-service")
public interface CategoryClient {

    @GetMapping("/categories/{catId}")
    CategoryDto getCategoryById(@PathVariable("catId") Long catId);

    @GetMapping("/categories")
    List<CategoryDto> getAllCategories(@RequestParam("from") int from,
                                       @RequestParam("size") int size);

    @GetMapping("/categories/batch")
    Map<Long, CategoryDto> getCategoriesByIds(@RequestParam("ids") List<Long> ids);
}