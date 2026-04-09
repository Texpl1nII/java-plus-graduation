package ru.practicum.event.client;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.stats.proto.ActionTypeProto;
import ru.practicum.stats.proto.EmptyResponse;
import ru.practicum.stats.proto.UserActionControllerGrpc;
import ru.practicum.stats.proto.UserActionMessage;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorGrpcClient {

    @GrpcClient("collector-service")
    private UserActionControllerGrpc.UserActionControllerBlockingStub collectorStub;

    /**
     * Отправить действие пользователя в Collector
     * @param userId ID пользователя
     * @param eventId ID мероприятия
     * @param actionType тип действия (VIEW, REGISTER, LIKE)
     */
    public void sendUserAction(long userId, long eventId, ActionTypeProto actionType) {
        try {
            UserActionMessage request = UserActionMessage.newBuilder()
                    .setUserId(userId)
                    .setEventId(eventId)
                    .setActionType(actionType)
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .setNanos(Instant.now().getNano())
                            .build())
                    .build();

            EmptyResponse response = collectorStub.collectUserAction(request);
            log.info("Sent user action: userId={}, eventId={}, actionType={}", userId, eventId, actionType);
        } catch (Exception e) {
            log.error("Failed to send user action to Collector: userId={}, eventId={}, actionType={}",
                    userId, eventId, actionType, e);
            // Не бросаем исключение, чтобы не нарушить основной поток
        }
    }
}
