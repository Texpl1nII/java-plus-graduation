package ru.practicum.analyzer.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.analyzer.model.RecommendedEvent;
import ru.practicum.stats.proto.RecommendedEventProto;

@UtilityClass
public class RecommendationMapper {

    public static RecommendedEventProto toProto(RecommendedEvent event) {
        return RecommendedEventProto.newBuilder()
                .setEventId(event.getEventId())
                .setScore(event.getScore())
                .build();
    }

    public static RecommendedEvent fromProto(RecommendedEventProto proto) {
        return new RecommendedEvent(proto.getEventId(), proto.getScore());
    }
}