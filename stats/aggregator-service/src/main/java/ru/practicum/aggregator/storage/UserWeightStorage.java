package ru.practicum.aggregator.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class UserWeightStorage {

    private final Map<Long, Map<Long, Double>> userWeights = new ConcurrentHashMap<>();

    public Double getWeight(long eventId, long userId) {
        Map<Long, Double> eventUsers = userWeights.get(eventId);
        if (eventUsers == null) {
            return null;
        }
        return eventUsers.get(userId);
    }

    public boolean updateWeight(long eventId, long userId, double newWeight) {
        Map<Long, Double> eventUsers = userWeights.computeIfAbsent(eventId, k -> new ConcurrentHashMap<>());

        Double oldWeight = eventUsers.get(userId);

        if (oldWeight != null && oldWeight >= newWeight) {
            log.debug("Weight not updated: userId={}, eventId={}, old={}, new={}",
                    userId, eventId, oldWeight, newWeight);
            return false;
        }

        eventUsers.put(userId, newWeight);
        log.info("Weight updated: userId={}, eventId={}, old={}, new={}",
                userId, eventId, oldWeight, newWeight);
        return true;
    }

    public Map<Long, Double> getUserEvents(long userId) {
        Map<Long, Double> result = new ConcurrentHashMap<>();

        for (Map.Entry<Long, Map<Long, Double>> eventEntry : userWeights.entrySet()) {
            long eventId = eventEntry.getKey();
            Double weight = eventEntry.getValue().get(userId);
            if (weight != null) {
                result.put(eventId, weight);
            }
        }

        return result;
    }

    public Map<Long, Double> getEventUsers(long eventId) {
        return userWeights.getOrDefault(eventId, Map.of());
    }
}
