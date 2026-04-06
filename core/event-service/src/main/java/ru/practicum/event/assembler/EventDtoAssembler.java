package ru.practicum.event.assembler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.StatClient;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.client.CategoryClient;
import ru.practicum.event.client.RequestClient;
import ru.practicum.event.client.UserClient;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventDtoAssembler {

    private final CategoryClient categoryClient;
    private final UserClient userClient;
    private final RequestClient requestClient;
    private final StatClient statClient;
    private final EventMapper eventMapper;

    // ========== ОСНОВНЫЕ МЕТОДЫ ДЛЯ СОБРАНИЯ DTO ==========

    public List<EventFullDto> toFullDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CategoryDto> categoriesMap = getCategoriesBatch(events);
        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> toFullDto(event, categoriesMap, views, confirmedRequests))
                .collect(Collectors.toList());
    }

    public List<EventShortDto> toShortDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CategoryDto> categoriesMap = getCategoriesBatch(events);
        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> toShortDto(event, categoriesMap, views, confirmedRequests))
                .collect(Collectors.toList());
    }

    public EventFullDto toFullDto(Event event) {
        return toFullDtoList(List.of(event)).get(0);
    }

    public EventShortDto toShortDto(Event event) {
        return toShortDtoList(List.of(event)).get(0);
    }

    // ========== ПРИВАТНЫЕ МЕТОДЫ СОБРАНИЯ ==========

    private EventFullDto toFullDto(Event event, Map<Long, CategoryDto> categoriesMap,
                                   Map<Long, Long> views, Map<Long, Long> confirmedRequests) {
        EventFullDto dto = eventMapper.toFullDto(event);
        dto.setViews(views.getOrDefault(event.getId(), 0L));
        dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
        dto.setCategory(getCategory(event.getCategoryId(), categoriesMap));
        dto.setInitiator(getUser(event.getInitiatorId()));
        return dto;
    }

    private EventShortDto toShortDto(Event event, Map<Long, CategoryDto> categoriesMap,
                                     Map<Long, Long> views, Map<Long, Long> confirmedRequests) {
        EventShortDto dto = eventMapper.toShortDto(event);
        dto.setViews(views.getOrDefault(event.getId(), 0L));
        dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
        dto.setCategory(getCategory(event.getCategoryId(), categoriesMap));
        dto.setInitiator(getUser(event.getInitiatorId()));
        return dto;
    }

    // ========== BATCH ЗАГРУЗКА КАТЕГОРИЙ ==========

    private Map<Long, CategoryDto> getCategoriesBatch(List<Event> events) {
        List<Long> categoryIds = events.stream()
                .map(Event::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<CategoryDto> categories = categoryClient.getCategoriesByIds(categoryIds);
            return categories.stream()
                    .collect(Collectors.toMap(CategoryDto::getId, c -> c, (e1, e2) -> e1));
        } catch (Exception e) {
            log.error("Failed to get categories batch: {}", categoryIds, e);
            return Collections.emptyMap();
        }
    }

    private CategoryDto getCategory(Long categoryId, Map<Long, CategoryDto> categoriesMap) {
        if (categoryId == null) {
            CategoryDto defaultCategory = new CategoryDto();
            defaultCategory.setId(0L);
            defaultCategory.setName("Unknown Category");
            return defaultCategory;
        }

        CategoryDto category = categoriesMap.get(categoryId);
        if (category == null) {
            category = new CategoryDto();
            category.setId(categoryId);
            category.setName("Unknown Category");
        }
        return category;
    }

    // ========== ПОЛУЧЕНИЕ USER ==========

    private UserShortDto getUser(Long userId) {
        if (userId == null) return null;
        try {
            UserDto user = userClient.getUserById(userId);
            return new UserShortDto(user.getId(), user.getName());
        } catch (Exception e) {
            log.warn("Failed to get user with id: {}", userId, e);
            UserShortDto defaultUser = new UserShortDto();
            defaultUser.setId(userId);
            defaultUser.setName("Unknown User");
            return defaultUser;
        }
    }

    // ========== ПОЛУЧЕНИЕ VIEWS ==========

    private Map<Long, Long> getViews(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> views = new HashMap<>();
        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());

        try {
            List<ViewStatsDto> stats = statClient.getStat(
                    LocalDateTime.now().minusYears(100),
                    LocalDateTime.now().plusYears(100),
                    uris, true);
            for (ViewStatsDto stat : stats) {
                String[] parts = stat.getUri().split("/");
                if (parts.length >= 3) {
                    Long eventId = Long.parseLong(parts[2]);
                    views.put(eventId, stat.getHits());
                }
            }
        } catch (Exception e) {
            log.error("Error getting stats", e);
        }
        return views;
    }

    // ========== ПОЛУЧЕНИЕ CONFIRMED REQUESTS ==========

    private Map<Long, Long> getConfirmedRequests(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        try {
            return requestClient.getConfirmedRequestsCounts(eventIds);
        } catch (Exception e) {
            log.error("Error getting confirmed requests counts for events {}", eventIds, e);
            return Collections.emptyMap();
        }
    }
}
