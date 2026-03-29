package ru.practicum.category.service;

import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.NewCategoryDto;

import java.util.List;

public interface CategoryService {
    CategoryDto create(NewCategoryDto newCategoryDto);

    void delete(Long catId);

    CategoryDto update(Long catId, CategoryDto categoryDto);

    List<CategoryDto> getAll(int from, int size);

    CategoryDto getById(Long catId);
}

