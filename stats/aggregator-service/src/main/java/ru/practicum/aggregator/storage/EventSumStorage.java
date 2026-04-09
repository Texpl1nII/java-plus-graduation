package ru.practicum.aggregator.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class EventSumStorage {

    // eventId -> S (сумма весов всех пользователей)
    private final Map<Long, Double> eventSums = new ConcurrentHashMap<>();

    public Double getSum(long eventId) {
        return eventSums.getOrDefault(eventId, 0.0);
    }

    public void updateSum(long eventId, double delta) {
        eventSums.compute(eventId, (k, oldSum) -> {
            double newSum = (oldSum == null ? 0 : oldSum) + delta;
            log.debug("Sum updated: eventId={}, old={}, delta={}, new={}",
                    eventId, oldSum, delta, newSum);
            return newSum;
        });
    }

    public Map<Long, Double> getAllSums() {
        return new ConcurrentHashMap<>(eventSums);
    }
}