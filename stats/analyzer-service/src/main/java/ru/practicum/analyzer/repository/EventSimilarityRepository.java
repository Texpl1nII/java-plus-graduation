package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.model.EventSimilarity;

import java.util.List;
import java.util.Optional;

public interface EventSimilarityRepository extends JpaRepository<EventSimilarity, Long> {

    Optional<EventSimilarity> findByEventAAndEventB(Long eventA, Long eventB);

    @Query("SELECT e FROM EventSimilarity e WHERE e.eventA = :eventId OR e.eventB = :eventId")
    List<EventSimilarity> findAllByEventId(@Param("eventId") Long eventId);

    @Query("SELECT e FROM EventSimilarity e WHERE e.eventA = :eventId OR e.eventB = :eventId ORDER BY e.score DESC")
    List<EventSimilarity> findTopSimilarByEventId(@Param("eventId") Long eventId);
}