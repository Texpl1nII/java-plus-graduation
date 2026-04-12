package ru.practicum.request.client;

import com.google.protobuf.Timestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.grpc.stats.collector.UserActionControllerGrpc;
import ru.practicum.grpc.stats.action.ActionTypeProto;
import ru.practicum.grpc.stats.action.UserActionProto;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorGrpcClient {

    @GrpcClient("collector-service")
    private UserActionControllerGrpc.UserActionControllerBlockingStub collectorStub;

    /**
     * Отправить действие REGISTER в Collector
     * @param userId ID пользователя
     * @param eventId ID мероприятия
     */
    public void sendRegisterAction(long userId, long eventId) {
        try {
            UserActionProto request = UserActionProto.newBuilder()
                    .setUserId(userId)
                    .setEventId(eventId)
                    .setActionType(ActionTypeProto.ACTION_REGISTER)
                    .setTimestamp(Timestamp.newBuilder()
                            .setSeconds(Instant.now().getEpochSecond())
                            .setNanos(Instant.now().getNano())
                            .build())
                    .build();

            collectorStub.collectUserAction(request);
            log.info("Sent REGISTER action to Collector: userId={}, eventId={}", userId, eventId);
        } catch (Exception e) {
            log.error("Failed to send REGISTER action to Collector: userId={}, eventId={}", userId, eventId, e);
        }
    }
}