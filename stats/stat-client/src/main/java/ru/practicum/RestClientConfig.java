package ru.practicum;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(@Value("${stats-client.url}") String baseUrl) {
        return RestClient.builder()
                .baseUrl(Objects.requireNonNull(baseUrl))
                .build();
    }
}

