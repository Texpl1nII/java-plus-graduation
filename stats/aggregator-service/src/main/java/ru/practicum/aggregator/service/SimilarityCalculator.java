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

        if (oldWeight != null && oldWeight >= newWeight) {
            log.debug("No weight change for userId={}, eventId={}, skipping", userId, eventId);
            return List.of();
        }

        // Обновляем вес пользователя для мероприятия
        userWeightStorage.updateWeight(eventId, userId, newWeight);

        // Обновляем сумму весов мероприятия
        double oldWeightValue = oldWeight == null ? 0 : oldWeight;
        double deltaWeight = newWeight - oldWeightValue;
        eventSumStorage.updateSum(eventId, deltaWeight);

        // Получаем ВСЕ мероприятия, с которыми взаимодействовал этот пользователь
        // ВНИМАНИЕ: нужен новый метод в UserWeightStorage!
        Map<Long, Double> userEvents = userWeightStorage.getUserEvents(userId);

        List<EventSimilarity> updatedSimilarities = new ArrayList<>();

        for (Map.Entry<Long, Double> otherEntry : userEvents.entrySet()) {
            long otherEventId = otherEntry.getKey();
            if (otherEventId == eventId) continue;

            double otherWeight = otherEntry.getValue();

            // Старый и новый вклад в S_min
            double oldMin = oldWeight == null ? 0 : Math.min(oldWeight, otherWeight);
            double newMin = Math.min(newWeight, otherWeight);
            double deltaMin = newMin - oldMin;

            if (deltaMin != 0) {
                minSumStorage.updateMinSum(eventId, otherEventId, deltaMin);

                double newSimilarity = calculateSimilarity(eventId, otherEventId);

                long eventA = Math.min(eventId, otherEventId);
                long eventB = Math.max(eventId, otherEventId);

                log.info("Updated similarity: eventA={}, eventB={}, newScore={}", eventA, eventB, newSimilarity);
                updatedSimilarities.add(new EventSimilarity(eventA, eventB, newSimilarity, timestamp));
            }
        }

        return updatedSimilarities;
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