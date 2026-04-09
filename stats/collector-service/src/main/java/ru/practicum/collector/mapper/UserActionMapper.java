package ru.practicum.collector.mapper;

import com.google.protobuf.Timestamp;
import lombok.experimental.UtilityClass;
import ru.practicum.collector.model.ActionType;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.stats.proto.UserActionMessage;

@UtilityClass
public class UserActionMapper {

    public static UserActionAvro toAvro(UserActionMessage protoMessage) {
        ActionType actionType = ActionType.fromProto(protoMessage.getActionType());

        // Конвертируем Protobuf Timestamp в milliseconds (long)
        long timestampMillis = protoMessage.getTimestamp().getSeconds() * 1000 +
                protoMessage.getTimestamp().getNanos() / 1_000_000;

        return UserActionAvro.newBuilder()
                .setUserId(protoMessage.getUserId())
                .setEventId(protoMessage.getEventId())
                .setActionType(ru.practicum.ewm.stats.avro.ActionTypeAvro.valueOf(actionType.name()))
                .setTimestamp(timestampMillis)  // ← long, НЕ Instant!
                .build();
    }
}