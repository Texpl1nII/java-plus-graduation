package ru.practicum.request.controller;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.event.service.EventService;
import ru.practicum.request.dto.ParticipationRequestDto;
import ru.practicum.request.service.ParticipationRequestService;

import java.util.List;

@Slf4j
@RestController
@AllArgsConstructor
@RequestMapping("/users/{userId}/requests")
@Validated
public class ParticipationRequestController {

    private final ParticipationRequestService requestService;
    private final EventService eventService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipationRequestDto create(@PathVariable @Positive Long userId,
                                          @RequestParam @Positive Long eventId) {
        log.info("POST: Создание запроса. Параметры ID пользователя: {}, ID события: {}", userId, eventId);
        return requestService.create(userId, eventId, eventService.getEventById(eventId));
    }

    @GetMapping
    public List<ParticipationRequestDto> getRequests(@PathVariable @Positive Long userId) {
        log.info("GET: Получение информации о заявках текущего userId={} на участие в чужих событиях", userId);
        List<ParticipationRequestDto> requests = requestService.getRequests(userId);
        log.info("Метод getRequests вернул {} запросов для пользователя с id={}", requests.size(), userId);
        return requests;
    }

    @PatchMapping("/{requestId}/cancel")
    public ParticipationRequestDto cancelRequest(@PathVariable @Positive Long userId,
                                                 @PathVariable @Positive Long requestId) {
        log.info("PATCH: Отмена участия userId={}, в requestId={}", userId, requestId);
        return requestService.cancelRequest(userId, requestId);
    }
}
