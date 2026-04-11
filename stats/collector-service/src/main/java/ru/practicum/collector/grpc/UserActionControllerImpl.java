package ru.practicum.collector.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.collector.kafka.UserActionProducer;
import ru.practicum.collector.mapper.UserActionMapper;
import ru.practicum.ewm.stats.proto.EmptyResponse;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionMessage;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserActionControllerImpl extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final UserActionProducer userActionProducer;

    @Override
    public void collectUserAction(UserActionMessage request, StreamObserver<EmptyResponse> responseObserver) {
        log.info("Received user action: userId={}, eventId={}, actionType={}, timestamp={}",
                request.getUserId(),
                request.getEventId(),
                request.getActionType(),
                request.getTimestamp());

        try {
            // Конвертируем Protobuf → Avro и отправляем в Kafka
            var avroMessage = UserActionMapper.toAvro(request);
            userActionProducer.send(avroMessage);

            log.info("Successfully sent to Kafka: {}", avroMessage);

            // Отправляем успешный ответ
            responseObserver.onNext(EmptyResponse.newBuilder().build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error processing user action", e);
            responseObserver.onError(e);
        }
    }
}
