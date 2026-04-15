package ru.practicum.collector.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActionProducer {

    private final KafkaTemplate<Long, SpecificRecordBase> kafkaTemplate;

    @Value("${kafka.topics.user-actions}")
    private String userActionsTopic;

    public void send(SpecificRecordBase message) {
        log.info("Sending message to topic {}: {}", userActionsTopic, message);
        kafkaTemplate.send(userActionsTopic, message);
    }
}