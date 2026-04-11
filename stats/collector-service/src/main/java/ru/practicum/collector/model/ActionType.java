package ru.practicum.collector.model;

import ru.practicum.ewm.stats.proto.ActionTypeProto;

public enum ActionType {
    VIEW,
    REGISTER,
    LIKE;

    public static ActionType fromProto(ActionTypeProto protoType) {
        return switch (protoType) {
            case ACTION_VIEW -> VIEW;
            case ACTION_REGISTER -> REGISTER;
            case ACTION_LIKE -> LIKE;
            default -> throw new IllegalArgumentException("Unknown action type: " + protoType);
        };
    }
}
