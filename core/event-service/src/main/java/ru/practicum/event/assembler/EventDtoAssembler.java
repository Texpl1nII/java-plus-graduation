package ru.practicum.event.assembler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.event.client.CategoryClient;
import ru.practicum.event.client.RequestClient;
import ru.practicum.event.client.UserClient;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.model.Event;
import ru.practicum.event.client.AnalyzerGrpcClient;
import ru.practicum.grpc.stats.recommendation.RecommendedEventProto;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventDtoAssembler {

    private final CategoryClient categoryClient;
    private final UserClient userClient;
    private final RequestClient requestClient;
    private final EventMapper eventMapper;
    private final AnalyzerGrpcClient analyzerGrpcClient;

    // ========== ОСНОВНЫЕ МЕТОДЫ ДЛЯ СОБРАНИЯ DTO ==========

    public List<EventFullDto> toFullDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CategoryDto> categoriesMap = getCategoriesBatch(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);
        Map<Long, Double> ratings = getRatings(events);

        return events.stream()
                .map(event -> toFullDto(event, categoriesMap, confirmedRequests, ratings))
                .collect(Collectors.toList());
    }

    public List<EventShortDto> toShortDtoList(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CategoryDto> categoriesMap = getCategoriesBatch(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);
        Map<Long, Double> ratings = getRatings(events);

        return events.stream()
                .map(event -> toShortDto(event, categoriesMap, confirmedRequests, ratings))
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
                                   Map<Long, Long> confirmedRequests,
                                   Map<Long, Double> ratings) {
        EventFullDto dto = eventMapper.toFullDto(event);
        dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
        dto.setRating(ratings.getOrDefault(event.getId(), event.getRating()));
        dto.setCategory(getCategory(event.getCategoryId(), categoriesMap));
        dto.setInitiator(getUser(event.getInitiatorId()));
        return dto;
    }

    private EventShortDto toShortDto(Event event, Map<Long, CategoryDto> categoriesMap,
                                     Map<Long, Long> confirmedRequests,
                                     Map<Long, Double> ratings) {
        EventShortDto dto = eventMapper.toShortDto(event);
        dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
        dto.setRating(ratings.getOrDefault(event.getId(), event.getRating()));
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

    // ========== ПОЛУЧЕНИЕ RATINGS ИЗ ANALYZER ==========

    private Map<Long, Double> getRatings(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        try {
            List<RecommendedEventProto> recommendedEvents = analyzerGrpcClient.getInteractionsCount(eventIds);

            if (recommendedEvents == null || recommendedEvents.isEmpty()) {
                log.debug("No ratings received from Analyzer for events: {}", eventIds);
                return Collections.emptyMap();
            }

            return recommendedEvents.stream()
                    .collect(Collectors.toMap(
                            RecommendedEventProto::getEventId,
                            RecommendedEventProto::getScore,
                            (e1, e2) -> e1
                    ));
        } catch (Exception e) {
            log.error("Error getting ratings from Analyzer for events {}", eventIds, e);
            return Collections.emptyMap();
        }
    }
}