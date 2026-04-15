package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.analyzer.repository.UserActionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final EventSimilarityRepository eventSimilarityRepository;
    private final UserActionRepository userActionRepository;
    private final PredictionService predictionService;

    /**
     * Получить похожие мероприятия (метод GetSimilarEvents)
     */
    public List<Map.Entry<Long, Double>> getSimilarEvents(long eventId, long userId, int maxResults) {
        log.info("Getting similar events for eventId={}, userId={}, maxResults={}", eventId, userId, maxResults);

        List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);
        Set<Long> interactedEvents = new HashSet<>(userActionRepository.findEventIdsByUserId(userId));

        List<Map.Entry<Long, Double>> similarEvents = new ArrayList<>();

        for (EventSimilarity sim : similarities) {
            long otherEventId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();

            if (otherEventId != eventId && !interactedEvents.contains(otherEventId)) {
                similarEvents.add(new AbstractMap.SimpleEntry<>(otherEventId, sim.getScore()));
            }
        }

        return similarEvents.stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Получить сумму взаимодействий для мероприятий (метод GetInteractionsCount)
     * ИСПРАВЛЕНО: использует прямой запрос к БД вместо findAll().stream()
     */
    public Map<Long, Double> getInteractionsCount(List<Long> eventIds) {
        log.info("Getting interactions count for eventIds: {}", eventIds);

        Map<Long, Double> result = new HashMap<>();

        for (Long eventId : eventIds) {
            Double sum = userActionRepository.sumWeightByEventId(eventId);
            result.put(eventId, sum != null ? sum : 0.0);
            log.debug("EventId={}, interactions sum={}", eventId, sum != null ? sum : 0.0);
        }

        return result;
    }

    /**
     * Получить рекомендации для пользователя (метод GetRecommendationsForUser)
     */
    public List<Map.Entry<Long, Double>> getRecommendationsForUser(long userId, int maxResults) {
        log.info("Getting recommendations for userId={}, maxResults={}", userId, maxResults);

        List<UserAction> userActions = userActionRepository.findByUserIdOrderByTimestampDesc(userId);

        if (userActions.isEmpty()) {
            log.info("No user actions found for userId={}", userId);
            return Collections.emptyList();
        }

        int limit = Math.min(10, userActions.size());
        List<UserAction> recentActions = userActions.subList(0, limit);

        Set<Long> interactedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toSet());

        Map<Long, Double> candidateScores = new HashMap<>();

        for (UserAction action : recentActions) {
            long eventId = action.getEventId();
            List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);

            for (EventSimilarity sim : similarities) {
                long otherEventId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();

                if (!interactedEvents.contains(otherEventId)) {
                    double currentScore = candidateScores.getOrDefault(otherEventId, 0.0);
                    candidateScores.put(otherEventId, currentScore + sim.getScore());
                }
            }
        }

        return candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }
}