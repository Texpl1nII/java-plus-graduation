package ru.practicum.request.status;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.request.dto.EventFullDto;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.model.ParticipationRequest;

@Slf4j
@Component
public class RequestStatusManager {

    public RequestStatus determineInitialStatus(EventFullDto event) {
        if (event.getParticipantLimit() == 0 || !event.getRequestModeration()) {
            return RequestStatus.CONFIRMED;
        }
        return RequestStatus.PENDING;
    }

    public void confirmRequest(ParticipationRequest request) {
        request.setStatus(RequestStatus.CONFIRMED);
        log.debug("Request {} confirmed", request.getId());
    }

    public void rejectRequest(ParticipationRequest request) {
        request.setStatus(RequestStatus.REJECTED);
        log.debug("Request {} rejected", request.getId());
    }

    public void cancelRequest(ParticipationRequest request) {
        request.setStatus(RequestStatus.CANCELED);
        log.debug("Request {} cancelled", request.getId());
    }
}