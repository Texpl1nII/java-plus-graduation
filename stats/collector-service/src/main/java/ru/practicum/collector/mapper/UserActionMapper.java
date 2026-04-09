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

        Timestamp protoTimestamp = protoMessage.getTimestamp();
        long timestampMillis = protoTimestamp.getSeconds() * 1000L +
                protoTimestamp.getNanos() / 1_000_000L;

        return UserActionAvro.newBuilder()
                .setUserId(protoMessage.getUserId())
                .setEventId(protoMessage.getEventId())
                .setActionType(ru.practicum.ewm.stats.avro.ActionTypeAvro.valueOf(actionType.name()))
                .setTimestamp(timestampMillis)
                .build();
    }
}