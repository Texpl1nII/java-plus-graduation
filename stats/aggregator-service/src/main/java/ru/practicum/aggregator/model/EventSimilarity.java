package ru.practicum.aggregator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventSimilarity {
    private long eventA;
    private long eventB;
    private double score;
    private long timestamp;
}