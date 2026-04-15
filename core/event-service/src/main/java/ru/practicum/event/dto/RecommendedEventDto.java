package ru.practicum.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor  // ← добавить эту аннотацию
@NoArgsConstructor
public class RecommendedEventDto {
    private Long eventId;
    private Double score;
}
