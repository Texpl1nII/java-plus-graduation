package ru.practicum.aggregator.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import ru.practicum.aggregator.model.EventSimilarity;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityProducer {

    private final KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    @Value("${kafka.topics.events-similarity}")
    private String similarityTopic;

    public void send(EventSimilarity similarity) {
        EventSimilarityAvro avroMessage = EventSimilarityAvro.newBuilder()
                .setEventA(similarity.getEventA())
                .setEventB(similarity.getEventB())
                .setScore(similarity.getScore())
                .setTimestamp(Instant.ofEpochMilli(similarity.getTimestamp()))
                .build();

        // Составной ключ из двух event ID
        String key = similarity.getEventA() + "-" + similarity.getEventB();

        log.info("Sending similarity to topic {}: key={}, eventA={}, eventB={}, score={}",
                similarityTopic, key, avroMessage.getEventA(), avroMessage.getEventB(), avroMessage.getScore());

        // Отправляем с ключом
        kafkaTemplate.send(similarityTopic, key, avroMessage);
    }
}