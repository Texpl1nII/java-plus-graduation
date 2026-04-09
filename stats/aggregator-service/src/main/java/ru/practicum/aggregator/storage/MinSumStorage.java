package ru.practicum.aggregator.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MinSumStorage {

    // eventA -> (eventB -> S_min) где eventA < eventB
    private final Map<Long, Map<Long, Double>> minSums = new ConcurrentHashMap<>();

    /**
     * Получить S_min для пары мероприятий (порядок не важен)
     */
    public double getMinSum(long eventId1, long eventId2) {
        long first = Math.min(eventId1, eventId2);
        long second = Math.max(eventId1, eventId2);

        Map<Long, Double> secondMap = minSums.get(first);
        if (secondMap == null) {
            return 0.0;
        }
        return secondMap.getOrDefault(second, 0.0);
    }

    /**
     * Обновить S_min для пары мероприятий
     */
    public void updateMinSum(long eventId1, long eventId2, double delta) {
        long first = Math.min(eventId1, eventId2);
        long second = Math.max(eventId1, eventId2);

        minSums.computeIfAbsent(first, k -> new ConcurrentHashMap<>())
                .compute(second, (k, oldValue) -> {
                    double newValue = (oldValue == null ? 0 : oldValue) + delta;
                    log.debug("S_min updated: pair({},{}), old={}, delta={}, new={}",
                            first, second, oldValue, delta, newValue);
                    return newValue;
                });
    }

    /**
     * Получить все похожие мероприятия для данного
     */
    public Map<Long, Double> getSimilarEvents(long eventId) {
        Map<Long, Double> result = new ConcurrentHashMap<>();

        // Ищем где eventId = first
        Map<Long, Double> asFirst = minSums.get(eventId);
        if (asFirst != null) {
            result.putAll(asFirst);
        }

        // Ищем где eventId = second
        for (Map.Entry<Long, Map<Long, Double>> entry : minSums.entrySet()) {
            long first = entry.getKey();
            if (first == eventId) continue;

            Double value = entry.getValue().get(eventId);
            if (value != null) {
                result.put(first, value);
            }
        }

        return result;
    }
}