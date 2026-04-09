package ru.practicum.aggregator.model;

public enum ActionType {
    VIEW(0.4),
    REGISTER(0.8),
    LIKE(1.0);

    private final double weight;

    ActionType(double weight) {
        this.weight = weight;
    }

    public double getWeight() {
        return weight;
    }

    public static ActionType fromAvro(ru.practicum.ewm.stats.avro.ActionTypeAvro avroType) {
        return switch (avroType) {
            case VIEW -> VIEW;
            case REGISTER -> REGISTER;
            case LIKE -> LIKE;
            default -> throw new IllegalArgumentException("Unknown action type: " + avroType);
        };
    }
}
