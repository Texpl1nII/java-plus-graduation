package ru.practicum.request.service;

import ru.practicum.request.dto.EventRequestStatusUpdateDto;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.List;

public interface ParticipationRequestService {

    // Создание запроса на участие
    ParticipationRequestDto create(Long userId, Long eventId);

    // Получение всех запросов пользователя
    List<ParticipationRequestDto> getRequests(Long userId);

    // Отмена своего запроса
    ParticipationRequestDto cancelRequest(Long userId, Long requestId);

    // Получение всех запросов на участие в событии (для инициатора)
    List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    // Изменение статуса запросов (подтверждение/отклонение)
    EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId,
                                                       EventRequestStatusUpdateDto updateDto);

    Long countByEventId(Long eventId);
}
