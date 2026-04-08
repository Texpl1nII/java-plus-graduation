package ru.practicum.request.limit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.request.dto.EventFullDto;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.exception.ConflictException;
import ru.practicum.request.repository.ParticipationRequestRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantLimitChecker {

    private final ParticipationRequestRepository repository;

    public int getConfirmedCount(Long eventId) {
        Long count = repository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        return count != null ? count.intValue() : 0;
    }

    public void checkAvailableSlots(EventFullDto event, int requestedCount) {
        int confirmedCount = getConfirmedCount(event.getId());
        int availableSlots = event.getParticipantLimit() - confirmedCount;

        if (requestedCount > availableSlots && event.getParticipantLimit() > 0) {
            throw new ConflictException("Not enough available slots. Available: " + availableSlots +
                    ", Requested: " + requestedCount);
        }
    }

    public int calculateRemainingSlots(EventFullDto event) {
        int confirmedCount = getConfirmedCount(event.getId());
        return event.getParticipantLimit() - confirmedCount;
    }
}
