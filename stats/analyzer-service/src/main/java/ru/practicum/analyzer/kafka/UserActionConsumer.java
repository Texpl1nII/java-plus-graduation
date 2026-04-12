package ru.practicum.analyzer.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.analyzer.model.ActionType;
import ru.practicum.analyzer.model.UserAction;
import ru.practicum.analyzer.repository.UserActionRepository;
import ru.practicum.ewm.stats.avro.UserActionAvro;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionConsumer {

    private final UserActionRepository userActionRepository;

    @KafkaListener(topics = "${kafka.topics.user-actions}", groupId = "analyzer-group")
    @Transactional
    public void consume(UserActionAvro action) {
        log.info("Received user action from Kafka: userId={}, eventId={}, actionType={}",
                action.getUserId(), action.getEventId(), action.getActionType());

        try {
            long userId = action.getUserId();
            long eventId = action.getEventId();
            double newWeight = ActionType.fromAvro(action.getActionType()).getWeight();

            long timestamp;
            Object timestampObj = action.getTimestamp();
            if (timestampObj instanceof Instant) {
                timestamp = ((Instant) timestampObj).toEpochMilli();
            } else if (timestampObj instanceof Long) {
                timestamp = (Long) timestampObj;
            } else {
                timestamp = System.currentTimeMillis();
            }

            Optional<UserAction> existingAction = userActionRepository.findByUserIdAndEventId(userId, eventId);

            if (existingAction.isPresent()) {
                UserAction userAction = existingAction.get();
                if (newWeight > userAction.getWeight()) {
                    log.info("Updating weight for userId={}, eventId={}: old={}, new={}",
                            userId, eventId, userAction.getWeight(), newWeight);
                    userAction.setWeight(newWeight);
                    userAction.setActionType(ActionType.fromAvro(action.getActionType()));
                    userAction.setTimestamp(timestamp);
                    userAction.setIsMax(true);
                    userActionRepository.save(userAction);
                } else {
                    log.debug("No update needed: newWeight <= oldWeight");
                }
            } else {
                UserAction newAction = new UserAction();
                newAction.setUserId(userId);
                newAction.setEventId(eventId);
                newAction.setActionType(ActionType.fromAvro(action.getActionType()));
                newAction.setWeight(newWeight);
                newAction.setTimestamp(timestamp);
                newAction.setIsMax(true);
                userActionRepository.save(newAction);
                log.info("Saved new user action: userId={}, eventId={}, weight={}",
                        userId, eventId, newWeight);
            }
        } catch (Exception e) {
            log.error("Error processing user action", e);
        }
    }
}
