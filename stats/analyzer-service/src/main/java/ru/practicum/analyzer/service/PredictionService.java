package ru.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.analyzer.repository.UserActionRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

    private final EventSimilarityRepository eventSimilarityRepository;
    private final UserActionRepository userActionRepository;

    private static final int DEFAULT_NEIGHBORS_COUNT = 10;

    public double predictRating(long userId, long eventId) {
        log.debug("Predicting rating for userId={}, eventId={}", userId, eventId);

        List<UserAction> userActions = userActionRepository.findMaxWeightsByUserId(userId);

        if (userActions.isEmpty()) {
            log.debug("No user actions found for userId={}", userId);
            return 0.0;
        }

        List<EventSimilarity> similarities = eventSimilarityRepository.findAllByEventId(eventId);

        List<EventSimilarity> topNeighbors = similarities.stream()
                .sorted(Comparator.comparing(EventSimilarity::getScore).reversed())
                .limit(DEFAULT_NEIGHBORS_COUNT)
                .collect(Collectors.toList());

        double weightedSum = 0.0;
        double similaritySum = 0.0;

        // Создаем Map для быстрого поиска
        Map<Long, Double> userRatings = userActions.stream()
                .collect(Collectors.toMap(
                        UserAction::getEventId,
                        UserAction::getWeight,
                        (existing, replacement) -> existing
                ));

        for (EventSimilarity sim : topNeighbors) {
            long neighborEventId = (sim.getEventA() == eventId) ? sim.getEventB() : sim.getEventA();

            Double weight = userRatings.get(neighborEventId);
            if (weight != null) {
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
