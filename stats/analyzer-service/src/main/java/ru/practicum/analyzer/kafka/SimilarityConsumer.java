package ru.practicum.analyzer.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.analyzer.model.EventSimilarity;
import ru.practicum.analyzer.repository.EventSimilarityRepository;
import ru.practicum.ewm.stats.avro.EventSimilarityAvro;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimilarityConsumer {

    private final EventSimilarityRepository eventSimilarityRepository;

    @KafkaListener(
            topics = "${kafka.topics.events-similarity}",
            groupId = "analyzer-group",
            containerFactory = "eventSimilarityKafkaListenerContainerFactory"  // ← ДОБАВИТЬ ЭТО
    )
    @Transactional
    public void consume(ConsumerRecord<String, EventSimilarityAvro> record) {
        // остальной код без изменений
        String key = record.key();
        EventSimilarityAvro similarity = record.value();

        log.info("Received similarity from Kafka: key={}, eventA={}, eventB={}, score={}",
                key, similarity.getEventA(), similarity.getEventB(), similarity.getScore());

        try {
            long eventA = similarity.getEventA();
            long eventB = similarity.getEventB();
            double score = similarity.getScore();

            long updatedAt;
            Object timestampObj = similarity.getTimestamp();
            if (timestampObj instanceof Instant) {
                updatedAt = ((Instant) timestampObj).toEpochMilli();
            } else if (timestampObj instanceof Long) {
                updatedAt = (Long) timestampObj;
            } else {
                updatedAt = System.currentTimeMillis();
            }

            Optional<EventSimilarity> existing = eventSimilarityRepository.findByEventAAndEventB(eventA, eventB);

            if (existing.isPresent()) {
                EventSimilarity eventSimilarity = existing.get();
                eventSimilarity.setScore(score);
                eventSimilarity.setUpdatedAt(updatedAt);
                eventSimilarityRepository.save(eventSimilarity);
                log.info("Updated similarity for pair ({}, {}): new score={}", eventA, eventB, score);
            } else {
                EventSimilarity newSimilarity = new EventSimilarity();
                newSimilarity.setEventA(eventA);
                newSimilarity.setEventB(eventB);
                newSimilarity.setScore(score);
                newSimilarity.setUpdatedAt(updatedAt);
                eventSimilarityRepository.save(newSimilarity);
                log.info("Saved new similarity for pair ({}, {}): score={}", eventA, eventB, score);
            }
        } catch (Exception e) {
            log.error("Error processing similarity", e);
        }
    }
}