package ru.practicum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
        String url = "http://" + instance.getHost() + ":" + instance.getPort() + path;
        log.info("Making URI: {}", url);
        return URI.create(url);
    }

    @Override
    public void hit(@NonNull EndpointHitDto paramHitDto) {
        log.info("Sending hit: {}", paramHitDto);
        RestClient restClient = restClientBuilder.build();
        restClient.post()
                .uri(makeUri("/hit"))
                .body(paramHitDto)
                .retrieve()
                .toBodilessEntity();
        log.info("Hit sent successfully");
    }

    @Override
    public List<ViewStatsDto> getStat(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        log.info("Getting stats: start={}, end={}, uris={}, unique={}", start, end, uris, unique);

        URI fullUri = makeUri("/stats");
        RestClient restClient = restClientBuilder.build();

        // Строим URL с параметрами
        StringBuilder urlBuilder = new StringBuilder(fullUri.toString());
        urlBuilder.append("?start=").append(start.format(FORMATTER));
        urlBuilder.append("&end=").append(end.format(FORMATTER));

        if (uris != null && !uris.isEmpty()) {
            for (String uri : uris) {
                urlBuilder.append("&uris=").append(uri);
            }
        }

        if (unique != null) {
            urlBuilder.append("&unique=").append(unique);
        }

        String url = urlBuilder.toString();
        log.info("Request URL: {}", url);

        List<ViewStatsDto> result = restClient.get()
                .uri(url)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ViewStatsDto>>() {});

        log.info("Stats result: {}", result);
        return result;
    }
}
