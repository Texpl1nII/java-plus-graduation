package ru.practicum.request.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.request.dto.EventFullDto;
import ru.practicum.request.enums.EventState;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.exception.ConflictException;
import ru.practicum.request.exception.DuplicatedException;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.ParticipationRequestRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final ParticipationRequestRepository repository;

    public void validateNotDuplicate(Long eventId, Long userId) {
        if (repository.findByEventIdAndRequesterId(eventId, userId).isPresent()) {
            throw new DuplicatedException("Request already exists");
        }
    }

    public void validateNotInitiator(Long userId, Long initiatorId) {
        if (initiatorId.equals(userId)) {
            throw new ConflictException("Event initiator cannot participate in their own event");
        }
    }

    public void validateEventPublished(EventFullDto event) {
        if (event.getState() != EventState.PUBLISHED) {
            throw new ConflictException("Cannot participate in unpublished event");
        }
    }

    public void validateParticipantLimit(EventFullDto event, int confirmedCount) {
        if (event.getParticipantLimit() > 0 && confirmedCount >= event.getParticipantLimit()) {
            throw new ConflictException("Participant limit has been reached");
        }
    }

    public void validateRequestStatusForCancel(ParticipationRequest request, Long userId) {
        if (!request.getRequesterId().equals(userId)) {
            throw new ConflictException("Cannot cancel another user's request");
        }
        if (request.getStatus() == RequestStatus.CONFIRMED) {
            throw new ConflictException("Cannot cancel already confirmed request");
        }
    }

    public void validateRequestStatusForChange(ParticipationRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new ConflictException("Request status must be PENDING");
        }
    }
}