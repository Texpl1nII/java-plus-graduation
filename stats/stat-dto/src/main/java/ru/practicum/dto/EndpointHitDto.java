package ru.practicum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndpointHitDto {
    private Long id;

    @NotBlank(message = "App name cannot be blank")
    private String app;

    @NotBlank(message = "URI cannot be blank")
    private String uri;

    @NotBlank(message = "IP address cannot be blank")
    private String ip;

    @NotBlank(message = "Timestamp cannot be blank")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}",
             message = "Timestamp must be in format yyyy-MM-dd HH:mm:ss")
    private String timestamp;
}

