package ru.practicum.aggregator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.practicum.aggregator.model.ActionType;
import ru.practicum.aggregator.model.EventSimilarity;
import ru.practicum.aggregator.storage.EventSumStorage;
import ru.practicum.aggregator.storage.MinSumStorage;
import ru.practicum.aggregator.storage.UserWeightStorage;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarityCalculator {

    private final UserWeightStorage userWeightStorage;
    private final EventSumStorage eventSumStorage;
    private final MinSumStorage minSumStorage;

    public List<EventSimilarity> processUserAction(UserActionAvro action) {
        long userId = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = ActionType.fromAvro(action.getActionType()).getWeight();
        long timestamp = action.getTimestamp().toEpochMilli();

        Double oldWeight = userWeightStorage.getWeight(eventId, userId);

        // Если вес не увеличился - ничего не меняем
        if (oldWeight != null && oldWeight >= newWeight) {
            log.debug("No weight increase for userId={}, eventId={}", userId, eventId);
            return List.of();
        }

        // Обновляем вес пользователя
        userWeightStorage.updateWeight(eventId, userId, newWeight);

        // Обновляем сумму весов мероприятия
        double oldWeightValue = oldWeight == null ? 0 : oldWeight;
        double deltaWeight = newWeight - oldWeightValue;
        eventSumStorage.updateSum(eventId, deltaWeight);

        // Получаем ВСЕ мероприятия пользователя (включая текущее)
        Map<Long, Double> userEvents = userWeightStorage.getUserEvents(userId);

        List<EventSimilarity> allSimilarities = new ArrayList<>();

        // Для каждого мероприятия пользователя (кроме текущего) отправляем similarity
        for (Map.Entry<Long, Double> otherEntry : userEvents.entrySet()) {
            long otherEventId = otherEntry.getKey();
            if (otherEventId == eventId) continue;

            double otherWeight = otherEntry.getValue();

            // Обновляем S_min (если нужно)
            double oldMin = oldWeight == null ? 0 : Math.min(oldWeight, otherWeight);
            double newMin = Math.min(newWeight, otherWeight);
            double deltaMin = newMin - oldMin;

            if (deltaMin != 0) {
                minSumStorage.updateMinSum(eventId, otherEventId, deltaMin);
            }

            // ВСЕГДА вычисляем актуальный similarity
            double similarity = calculateSimilarity(eventId, otherEventId);

            long eventA = Math.min(eventId, otherEventId);
            long eventB = Math.max(eventId, otherEventId);

            log.info("Similarity for pair ({}, {}): score={}", eventA, eventB, similarity);

            // Добавляем ВСЕ пары, даже если score не изменился
            allSimilarities.add(new EventSimilarity(eventA, eventB, similarity, timestamp));
        }

        return allSimilarities;
    }

    public double calculateSimilarity(long eventA, long eventB) {
        double sMin = minSumStorage.getMinSum(eventA, eventB);
        double sA = eventSumStorage.getSum(eventA);
        double sB = eventSumStorage.getSum(eventB);

        if (sA <= 0 || sB <= 0) {
            return 0.0;
        }

        return sMin / (Math.sqrt(sA) * Math.sqrt(sB));
    }
}