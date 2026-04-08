package ru.practicum.event.access;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.practicum.event.client.UserClient;
import ru.practicum.event.exception.NotFoundException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventAccessManager {

    private final UserClient userClient;

    public void checkUserExists(Long userId) {
        if (userId == null) {
            throw new NotFoundException("User not found");
        }
        try {
            userClient.getUserById(userId);
        } catch (Exception e) {
            log.error("User not found: {}", userId, e);
            throw new NotFoundException("User with id=" + userId + " was not found");
        }
    }
}