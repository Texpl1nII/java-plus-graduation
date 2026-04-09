package ru.practicum.analyzer.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @KafkaListener(topics = "${kafka.topics.events-similarity}", groupId = "analyzer-group")
    @Transactional
    public void consume(EventSimilarityAvro similarity) {
        log.info("Received similarity from Kafka: eventA={}, eventB={}, score={}",
                similarity.getEventA(), similarity.getEventB(), similarity.getScore());

        try {
            long eventA = similarity.getEventA();
            long eventB = similarity.getEventB();
            double score = similarity.getScore();
            Instant updatedAt = Instant.ofEpochMilli(similarity.getTimestamp());

            // Проверяем, есть ли уже запись о сходстве
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