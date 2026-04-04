package ru.practicum.category.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.category.client.EventClient;
import ru.practicum.category.dto.CategoryDto;
import ru.practicum.category.dto.EventShortDto;
import ru.practicum.category.dto.NewCategoryDto;
import ru.practicum.category.exception.ConflictException;
import ru.practicum.category.exception.NotFoundException;
import ru.practicum.category.mapper.CategoryMapper;
import ru.practicum.category.model.Category;
import ru.practicum.category.repository.CategoryRepository;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final EventClient eventClient;

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
        log.info("Deleting category with id: {}", catId);

        Category category = getCategoryById(catId);

        try {
            List<EventShortDto> events = eventClient.getEventsByCategoryId(catId);
            if (events != null && !events.isEmpty()) {
                log.warn("Cannot delete category {} - has {} events", catId, events.size());
                throw new ConflictException("Cannot delete category with existing events");
            }
            categoryRepository.deleteById(catId);
            log.info("Category {} deleted successfully", catId);
        } catch (FeignException.NotFound e) {
            // 404 значит нет событий - можно удалять
            categoryRepository.deleteById(catId);
            log.info("Category {} deleted successfully (no events found)", catId);
        } catch (FeignException e) {
            log.error("Error checking events for category {}: status={}", catId, e.status());
            throw new ConflictException("Cannot delete category: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public CategoryDto update(Long catId, CategoryDto categoryDto) {
        Category category = getCategoryById(catId);

        // Проверяем, что новое имя не занято другой категорией
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

    // ✅ НОВАЯ РЕАЛИЗАЦИЯ - получение категорий по списку ID
    @Override
    public List<CategoryDto> getCategoriesByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            log.info("Empty ids list, returning empty list");
            return Collections.emptyList();
        }

        log.info("Getting categories by ids: {}", ids);
        List<Category> categories = categoryRepository.findAllById(ids);
        log.info("Found {} categories out of {} requested", categories.size(), ids.size());

        return categories.stream()
                .map(categoryMapper::toDto)
                .collect(Collectors.toList());
    }

    private Category getCategoryById(Long catId) {
        return categoryRepository.findById(catId)
                .orElseThrow(() -> new NotFoundException("Category with id=" + catId + " was not found"));
    }
}