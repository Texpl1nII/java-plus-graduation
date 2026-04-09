package ru.practicum.event.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.practicum.client.AnalyzerGrpcClient;
import ru.practicum.client.CollectorGrpcClient;
import ru.practicum.event.dto.RecommendedEventDto;
import ru.practicum.event.service.EventService;
import ru.practicum.stats.proto.ActionTypeProto;
import ru.practicum.stats.proto.RecommendedEvent;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RecommendationController {

    private final AnalyzerGrpcClient analyzerGrpcClient;
    private final CollectorGrpcClient collectorGrpcClient;
    private final EventService eventService;

    /**
     * GET /events/recommendations - получить рекомендации для пользователя
     */
    @GetMapping("/events/recommendations")
    public List<RecommendedEventDto> getRecommendations(
            @RequestHeader("X-EWM-USER-ID") long userId,
            @RequestParam(defaultValue = "10") int maxResults) {

        log.info("GET /events/recommendations: userId={}, maxResults={}", userId, maxResults);

        List<RecommendedEvent> recommendations = analyzerGrpcClient.getRecommendationsForUser(userId, maxResults);

        return recommendations.stream()
                .map(re -> new RecommendedEventDto(re.getEventId(), re.getScore()))
                .collect(Collectors.toList());
    }

    /**
     * PUT /events/{eventId}/like - поставить лайк мероприятию
     */
    @PutMapping("/events/{eventId}/like")
    public void likeEvent(
            @RequestHeader("X-EWM-USER-ID") long userId,
            @PathVariable long eventId) {

        log.info("PUT /events/{}/like: userId={}", eventId, userId);

        // Проверяем, что пользователь зарегистрирован на мероприятие
        boolean isRegistered = eventService.isUserRegisteredOnEvent(userId, eventId);

        if (!isRegistered) {
            throw new IllegalArgumentException("User must be registered to the event before liking it");
        }

        // Отправляем LIKE в Collector
        collectorGrpcClient.sendUserAction(userId, eventId, ActionTypeProto.ACTION_LIKE);
    }
}
