package ru.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.analyzer.model.UserAction;

import java.util.List;
import java.util.Optional;

public interface UserActionRepository extends JpaRepository<UserAction, Long> {

    Optional<UserAction> findByUserIdAndEventId(Long userId, Long eventId);

    List<UserAction> findByUserIdOrderByTimestampDesc(Long userId);

    @Query("SELECT DISTINCT u.eventId FROM UserAction u WHERE u.userId = :userId")
    List<Long> findEventIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT u FROM UserAction u WHERE u.userId = :userId AND u.isMax = true")
    List<UserAction> findMaxWeightsByUserId(@Param("userId") Long userId);

    // НОВЫЙ МЕТОД - ОБЯЗАТЕЛЬНО ДОБАВИТЬ!
    @Query("SELECT COALESCE(SUM(u.weight), 0.0) FROM UserAction u WHERE u.eventId = :eventId")
    Double sumWeightByEventId(@Param("eventId") Long eventId);
}