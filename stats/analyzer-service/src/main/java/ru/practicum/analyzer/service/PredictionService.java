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
public class PredictionService {

    private final EventSimilarityRepository eventSimilarityRepository;
    private final UserActionRepository userActionRepository;

    private static final int DEFAULT_NEIGHBORS_COUNT = 10;

    /**
     * Предсказать оценку для мероприятия по формуле:
     * r̂ = Σ(sim * weight) / Σ(sim)
     */
    public double predictRating(long userId, long eventId) {
        log.debug("Predicting rating for userId={}, eventId={}", userId, eventId);

        // Получаем все действия пользователя
        List<UserAction> userActions = userActionRepository.findMaxWeightsByUserId(userId);

        if (userActions.isEmpty()) {
            log.debug("No user actions found for userId={}", userId);
            return 0.0;
        }

        // Находим K ближайших соседей (мероприятий, похожих на eventId)
        List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);

        // Сортируем по score и берем топ-K
        List<EventSimilarity> topNeighbors = similarities.stream()
                .sorted(Comparator.comparing(EventSimilarity::getScore).reversed())
                .limit(DEFAULT_NEIGHBORS_COUNT)
                .collect(Collectors.toList());

        double weightedSum = 0.0;
        double similaritySum = 0.0;

        for (EventSimilarity sim : topNeighbors) {
            long neighborEventId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();

            // Ищем оценку пользователя для соседнего мероприятия
            Optional<UserAction> userAction = userActions.stream()
                    .filter(a -> a.getEventId() == neighborEventId)
                    .findFirst();

            if (userAction.isPresent()) {
                double weight = userAction.get().getWeight();
                double similarity = sim.getScore();

                weightedSum += similarity * weight;
                similaritySum += similarity;
            }
        }

        if (similaritySum == 0.0) {
            return 0.0;
        }

        double predictedRating = weightedSum / similaritySum;
        log.debug("Predicted rating for userId={}, eventId={}: {}", userId, eventId, predictedRating);

        return predictedRating;
    }
}
