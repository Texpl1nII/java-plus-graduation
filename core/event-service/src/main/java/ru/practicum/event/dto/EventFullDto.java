package ru.practicum.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.event.model.EventState;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventFullDto {
    private Long id;
    private String title;
    private String annotation;
    private String description;
    private CategoryDto category;      // объект
    private Boolean paid;
    private UserShortDto initiator;    // объект
    private LocationDto location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdOn;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishedOn;

    private Integer participantLimit;
    private Boolean requestModeration;
    private EventState state;

    private Long confirmedRequests;
    private Long views;

    // Геттеры для обратной совместимости
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getCategoryId() {
        return category != null ? category.getId() : null;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getInitiatorId() {
        return initiator != null ? initiator.getId() : null;
    }
}