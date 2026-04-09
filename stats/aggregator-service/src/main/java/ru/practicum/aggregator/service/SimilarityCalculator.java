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
     * @return EventSimilarity если нужно отправить обновления, иначе null
     */
    public EventSimilarity processUserAction(UserActionAvro action) {
        long userId = action.getUserId();
        long eventId = action.getEventId();
        double newWeight = ActionType.fromAvro(action.getActionType()).getWeight();
        long timestamp = action.getTimestamp();

        // Получаем старый вес
        Double oldWeight = userWeightStorage.getWeight(eventId, userId);

        // Если вес не изменился (был такой же или выше) — ничего не делаем
        if (oldWeight != null && oldWeight >= newWeight) {
            log.debug("No weight change for userId={}, eventId={}, skipping", userId, eventId);
            return null;
        }

        // Обновляем вес
        userWeightStorage.updateWeight(eventId, userId, newWeight);

        // Считаем дельту для S_event
        double oldWeightValue = oldWeight == null ? 0 : oldWeight;
        double deltaWeight = newWeight - oldWeightValue;
        eventSumStorage.updateSum(eventId, deltaWeight);

        // Обновляем S_min для всех пар с этим мероприятием
        Map<Long, Double> otherEvents = userWeightStorage.getEventUsers(eventId);

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

                log.info("Updated similarity: eventA={}, eventB={}, similarity={}",
                        Math.min(eventId, otherEventId),
                        Math.max(eventId, otherEventId),
                        similarity);

                // Возвращаем обновление для отправки в Kafka
                return new EventSimilarity(
                        Math.min(eventId, otherEventId),
                        Math.max(eventId, otherEventId),
                        similarity,
                        timestamp
                );
            }
        }

        return null;
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
}
