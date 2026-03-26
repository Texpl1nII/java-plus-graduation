package ru.practicum;

import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public interface StatClient {
    void hit(@NonNull EndpointHitDto paramHitDto);

    List<ViewStatsDto> getStat(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique);
}

