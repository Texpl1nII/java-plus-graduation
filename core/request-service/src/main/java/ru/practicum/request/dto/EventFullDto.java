package ru.practicum.request.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.practicum.request.enums.EventState;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventFullDto {
    private Long id;
    private String title;
    private String annotation;
    private String description;

    // Поля для обратной совместимости (могут быть null)
    private Long categoryId;
    private Long initiatorId;

    // Объекты, которые приходят от event-service
    private CategoryDto category;
    private UserShortDto initiator;

    private Boolean paid;
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

    // READ-ONLY геттеры для обратной совместимости
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getCategoryId() {
        if (category != null) {
            return category.getId();
        }
        return categoryId;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public Long getInitiatorId() {
        if (initiator != null) {
            return initiator.getId();
        }
        return initiatorId;
    }
}