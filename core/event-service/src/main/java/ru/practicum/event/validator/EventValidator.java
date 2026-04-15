package ru.practicum.event.validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.event.exception.ConflictException;
import ru.practicum.event.exception.NotFoundException;
import ru.practicum.event.exception.ValidationException;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.EventState;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventValidator {

    public void validateEventDateForCreate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }
    }

    public void validateEventDateForUpdate(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }
    }

    public void validateEventDateForAdmin(LocalDateTime eventDate) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(1))) {
            throw new ValidationException("Event date must be at least 1 hour from now");
        }
    }

    public void validateUserCanModify(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
    }

    public void validateAdminCanPublish(Event event) {
        if (event.getState() != EventState.PENDING) {
            throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
        }
    }

    public void validateAdminCanReject(Event event) {
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Cannot reject the event because it's already published");
        }
    }

    public void validateEventPublished(Event event) {
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event must be published");
        }
    }
}