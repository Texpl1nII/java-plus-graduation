package ru.practicum;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StatClientImpl implements StatClient {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RestClient restClient;

    @Override
    public void hit(@NonNull EndpointHitDto paramHitDto) {
        restClient.post()
                .uri("/hit")
                .body(paramHitDto)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public List<ViewStatsDto> getStat(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {

        return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/stats")
                            .queryParam("start", start.format(FORMATTER))
                            .queryParam("end", end.format(FORMATTER));

                    if (uris != null && !uris.isEmpty()) {
                        builder.queryParam("uris", uris);
                    }

                    if (unique != null) {
                        builder.queryParam("unique", unique);
                    }

                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<List<ViewStatsDto>>() {});
    }
}

