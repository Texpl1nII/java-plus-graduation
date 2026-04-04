package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.request.dto.RequestCount;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.model.ParticipationRequest;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long requesterId);

    List<ParticipationRequest> findAllByEventIdAndStatus(Long eventId, RequestStatus status);

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByIdIn(List<Long> ids);

    Long countByEventIdAndStatus(Long eventId, RequestStatus status);

    Long countByEventId(Long eventId);

    // ========== BATCH-МЕТОД ДЛЯ НЕСКОЛЬКИХ СОБЫТИЙ ==========
    @Query("SELECT new ru.practicum.request.dto.RequestCount(r.eventId, COUNT(r)) " +
            "FROM ParticipationRequest r " +
            "WHERE r.eventId IN :eventIds AND r.status = :status " +
            "GROUP BY r.eventId")
    List<RequestCount> countByEventIdInAndStatus(@Param("eventIds") List<Long> eventIds,
                                                 @Param("status") RequestStatus status);
}
