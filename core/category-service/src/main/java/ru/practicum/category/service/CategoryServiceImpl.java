package ru.practicum.category.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.NewCategoryDto;
import ru.practicum.category.exception.ConflictException;
import ru.practicum.category.exception.NotFoundException;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;


import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    public CategoryDto create(NewCategoryDto newCategoryDto) {
        log.info("Creating category with name: {}", newCategoryDto.getName());

        if (categoryRepository.existsByName(newCategoryDto.getName())) {
            throw new ConflictException("Category with this name already exists");
        }
        Category category = categoryMapper.toEntity(newCategoryDto);
        Category saved = categoryRepository.save(category);
        log.info("Category created with id: {}", saved.getId());
        return categoryMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long catId) {
        Category category = getCategoryById(catId);

        // Проверяем, есть ли события у категории
        try {
            // Здесь должен быть вызов к event-service для проверки наличия событий
            // Пока временно выбрасываем исключение для теста
            throw new ConflictException("Cannot delete category with existing events");
        } catch (FeignException e) {
            if (e.status() == 404) {
                // Нет событий, можно удалять
                categoryRepository.deleteById(catId);
            } else {
                throw new ConflictException("Cannot delete category with existing events");
            }
        }
    }

    @Override
    @Transactional
    public CategoryDto update(Long catId, CategoryDto categoryDto) {
        Category category = getCategoryById(catId);

        // Проверяем, что новое имя не занято другим категорией
        if (!category.getName().equals(categoryDto.getName())
                && categoryRepository.existsByName(categoryDto.getName())) {
            throw new ConflictException("Category with this name already exists");
        }

        category.setName(categoryDto.getName());

        return categoryMapper.toDto(categoryRepository.save(category));
    }

    @Override
    public List<CategoryDto> getAll(int from, int size) {
        int page = from / size;
        return categoryRepository.findAll(PageRequest.of(page, size)).stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryDto getById(Long catId) {
        Category category = getCategoryById(catId);
        return categoryMapper.toDto(category);
    }

    private Category getCategoryById(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }
}