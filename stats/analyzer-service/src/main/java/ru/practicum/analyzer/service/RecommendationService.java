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

        // Получаем все мероприятия, похожие на указанное
        List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);

        // Получаем мероприятия, с которыми пользователь уже взаимодействовал
        Set<Long> interactedEvents = new HashSet<>(userActionRepository.findEventIdsByUserId(userId));

        // Фильтруем: исключаем те, с которыми пользователь уже взаимодействовал
        // и исключаем само мероприятие
        List<Map.Entry<Long, Double>> similarEvents = new ArrayList<>();

        for (EventSimilarity sim : similarities) {
            long otherEventId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();

            if (otherEventId != eventId && !interactedEvents.contains(otherEventId)) {
                similarEvents.add(new AbstractMap.SimpleEntry<>(otherEventId, sim.getScore()));
            }
        }

        // Сортируем по убыванию score и ограничиваем maxResults
        return similarEvents.stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Получить сумму взаимодействий для мероприятий (метод GetInteractionsCount)
     */
    public Map<Long, Double> getInteractionsCount(List<Long> eventIds) {
        log.info("Getting interactions count for eventIds: {}", eventIds);

        Map<Long, Double> result = new HashMap<>();

        for (Long eventId : eventIds) {
            List<UserAction> actions = userActionRepository.findAll().stream()
                    .filter(a -> a.getEventId().equals(eventId))
                    .collect(Collectors.toList());

            double sum = actions.stream()
                    .mapToDouble(UserAction::getWeight)
                    .sum();

            result.put(eventId, sum);
        }

        return result;
    }

    /**
     * Получить рекомендации для пользователя (метод GetRecommendationsForUser)
     */
    public List<Map.Entry<Long, Double>> getRecommendationsForUser(long userId, int maxResults) {
        log.info("Getting recommendations for userId={}, maxResults={}", userId, maxResults);

        // Получаем последние 10 взаимодействий пользователя
        List<UserAction> userActions = userActionRepository.findByUserIdOrderByTimestampDesc(userId);

        if (userActions.isEmpty()) {
            log.info("No user actions found for userId={}", userId);
            return Collections.emptyList();
        }

        // Берем последние 10 (или меньше)
        int limit = Math.min(10, userActions.size());
        List<UserAction> recentActions = userActions.subList(0, limit);

        // Получаем ID мероприятий, с которыми пользователь уже взаимодействовал
        Set<Long> interactedEvents = userActions.stream()
                .map(UserAction::getEventId)
                .collect(Collectors.toSet());

        // Для каждого недавнего мероприятия находим похожие
        Map<Long, Double> candidateScores = new HashMap<>();

        for (UserAction action : recentActions) {
            long eventId = action.getEventId();
            List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);

            for (EventSimilarity sim : similarities) {
                long otherEventId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();

                // Пропускаем уже взаимодействованные мероприятия
                if (interactedEvents.contains(otherEventId)) {
                    continue;
                }

                // Накопление score (чем больше похожих мероприятий, тем выше рейтинг)
                double currentScore = candidateScores.getOrDefault(otherEventId, 0.0);
                candidateScores.put(otherEventId, currentScore + sim.getScore());
            }
        }

        // Сортируем и ограничиваем
        return candidateScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }
}