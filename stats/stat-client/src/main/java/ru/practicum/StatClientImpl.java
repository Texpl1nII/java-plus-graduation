package ru.practicum;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.ServiceInstance;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
public class StatClientImpl implements StatClient {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DiscoveryClient discoveryClient;
    private final RestClient.Builder restClientBuilder;
    private static final String STATS_SERVICE_ID = "stats-server";

    private ServiceInstance getInstance() {
        List<ServiceInstance> instances = discoveryClient.getInstances(STATS_SERVICE_ID);
        if (instances == null || instances.isEmpty()) {
            throw new RuntimeException("Сервис статистики не найден в Eureka");
        }
        return instances.get(0);
    }

    private URI makeUri(String path) {
        ServiceInstance instance = getInstance();
        return URI.create("http://" + instance.getHost() + ":" + instance.getPort() + path);
    }

    @Override
    public void hit(@NonNull EndpointHitDto paramHitDto) {
        RestClient restClient = restClientBuilder.build();
        restClient.post()
                .uri(makeUri("/hit"))
                .body(paramHitDto)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public List<ViewStatsDto> getStat(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        RestClient restClient = restClientBuilder.build();

        URI uri = makeUri("/stats");

        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(uri.getPath())
                        .queryParam("start", start.format(FORMATTER))
                        .queryParam("end", end.format(FORMATTER))
                        .queryParam("uris", uris != null && !uris.isEmpty() ? uris : null)
                        .queryParam("unique", unique)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<ViewStatsDto>>() {});
    }
}
