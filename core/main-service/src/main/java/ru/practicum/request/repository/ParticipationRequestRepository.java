package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.practicum.request.model.ParticipationRequest;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long userId);

    List<ParticipationRequest> findAllByEventIdAndStatus(Long eventId, ru.practicum.request.enums.RequestStatus status);

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);

    Long countByEventIdAndStatus(Long eventId, ru.practicum.request.enums.RequestStatus status);

    @Query("SELECT r.event.id AS eventId, count(r.id) AS count FROM ParticipationRequest r WHERE r.event.id IN ?1 AND r.status = ?2 GROUP BY r.event.id")
    List<ConfirmedRequestView> countByEventIdInAndStatus(List<Long> eventIds, ru.practicum.request.enums.RequestStatus status);

    List<ParticipationRequest> findAllByEventId(Long eventId);

    List<ParticipationRequest> findAllByIdIn(List<Long> ids);
}
