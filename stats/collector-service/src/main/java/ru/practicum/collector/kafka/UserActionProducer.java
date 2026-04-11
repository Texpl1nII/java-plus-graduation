package ru.practicum.collector.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.practicum.ewm.stats.avro.UserActionAvro;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProducer {

    private final KafkaTemplate<String, UserActionAvro> kafkaTemplate;

    @Value("${kafka.topics.user-actions}")
    private String topic;

    public void send(UserActionAvro userAction) {
        log.info("Sending message to topic {}: {}", topic, userAction);
        kafkaTemplate.send(topic, String.valueOf(userAction.getUserId()), userAction)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Message sent successfully to offset: {}",
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send message", ex);
                    }
                });
    }
}