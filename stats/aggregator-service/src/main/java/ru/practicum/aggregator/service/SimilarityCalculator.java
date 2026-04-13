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

    /**
     * Обработка нового действия пользователя
     * @return List<EventSimilarity> список обновленных пар (может быть пустым)
     */
    public List<EventSimilarity> processUserAction(UserActionAvro action) {
        long userId = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = ActionType.fromAvro(action.getActionType()).getWeight();
        long timestamp = action.getTimestamp().toEpochMilli();

        // Получаем старый вес
        Double oldWeight = userWeightStorage.getWeight(eventId, userId);

        // Если вес не изменился (был такой же или выше) — ничего не делаем
        if (oldWeight != null && oldWeight >= newWeight) {
            log.debug("No weight change for userId={}, eventId={}, skipping", userId, eventId);
            return List.of();
        }

        // Обновляем вес
        userWeightStorage.updateWeight(eventId, userId, newWeight);

        // Считаем дельту для S_event
        double oldWeightValue = oldWeight == null ? 0 : oldWeight;
        double deltaWeight = newWeight - oldWeightValue;
        eventSumStorage.updateSum(eventId, deltaWeight);

        // Обновляем S_min для всех пар с этим мероприятием
        Map<Long, Double> otherEvents = userWeightStorage.getEventUsers(eventId);
        List<EventSimilarity> updatedSimilarities = new ArrayList<>();

        for (Map.Entry<Long, Double> otherEntry : otherEvents.entrySet()) {
            long otherEventId = otherEntry.getKey();
            if (otherEventId == eventId) continue;

            double otherWeight = otherEntry.getValue();

            // Старый вклад в S_min
            double oldMin = oldWeight == null ? 0 : Math.min(oldWeight, otherWeight);
            // Новый вклад в S_min
            double newMin = Math.min(newWeight, otherWeight);
            double deltaMin = newMin - oldMin;

            if (deltaMin != 0) {
                minSumStorage.updateMinSum(eventId, otherEventId, deltaMin);

                // Пересчитываем similarity
                double similarity = calculateSimilarity(eventId, otherEventId);

                long eventA = Math.min(eventId, otherEventId);
                long eventB = Math.max(eventId, otherEventId);
                double oldSimilarity = getCurrentSimilarity(eventA, eventB);

                // Отправляем только если similarity изменился
                if (Math.abs(oldSimilarity - similarity) > 0.0001) {
                    log.info("Updated similarity: eventA={}, eventB={}, oldScore={}, newScore={}",
                            eventA, eventB, oldSimilarity, similarity);

                    updatedSimilarities.add(new EventSimilarity(eventA, eventB, similarity, timestamp));
                }
            }
        }

        return updatedSimilarities;
    }

    /**
     * Вычисление косинусного сходства по формуле:
     * similarity = S_min(A,B) / (sqrt(S_A) * sqrt(S_B))
     */
    public double calculateSimilarity(long eventA, long eventB) {
        double sMin = minSumStorage.getMinSum(eventA, eventB);
        double sA = eventSumStorage.getSum(eventA);
        double sB = eventSumStorage.getSum(eventB);

        if (sA == 0 || sB == 0) {
            return 0.0;
        }

        return sMin / (Math.sqrt(sA) * Math.sqrt(sB));
    }

    /**
     * Получить текущее значение similarity из хранилища
     */
    private double getCurrentSimilarity(long eventA, long eventB) {
        double sMin = minSumStorage.getMinSum(eventA, eventB);
        double sA = eventSumStorage.getSum(eventA);
        double sB = eventSumStorage.getSum(eventB);

        if (sA == 0 || sB == 0) {
            return 0.0;
        }
        return sMin / (Math.sqrt(sA) * Math.sqrt(sB));
    }
}