package ru.practicum.aggregator.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.practicum.aggregator.model.EventSimilarity;
import ru.practicum.aggregator.service.SimilarityCalculator;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionConsumer {

    private final SimilarityCalculator similarityCalculator;
    private final SimilarityProducer similarityProducer;

    @KafkaListener(topics = "${kafka.topics.user-actions}", groupId = "aggregator-group")
    public void consume(UserActionAvro action) {
        log.info("Received user action: userId={}, eventId={}, actionType={}",
                action.getUserId(), action.getEventId(), action.getActionType());

        try {
            EventSimilarity similarity = similarityCalculator.processUserAction(action);

            if (similarity != null) {
                similarityProducer.send(similarity);
            }
        } catch (Exception e) {
            log.error("Error processing user action", e);
        }
    }
}
