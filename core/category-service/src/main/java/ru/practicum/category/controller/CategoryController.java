package ru.practicum.category.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryDto> getAll(@RequestParam(defaultValue = "0") @PositiveOrZero int from,
                                    @RequestParam(defaultValue = "10") @Positive int size) {
        log.info("Get all categories from={}, size={}", from, size);
        return categoryService.getAll(from, size);
    }

    @GetMapping("/{catId}")
    public CategoryDto getById(@PathVariable Long catId) {
        log.info("Get category by id: {}", catId);
        CategoryDto category = categoryService.getById(catId);
        log.info("Found category: {}", category);
        return category;
    }

    // ✅ НОВЫЙ МЕТОД - batch получение категорий по списку ID
    @GetMapping("/batch")
    public List<CategoryDto> getCategoriesByIds(@RequestParam List<Long> ids) {
        log.info("Get categories by ids: {}", ids);
        return categoryService.getCategoriesByIds(ids);
    }
}

