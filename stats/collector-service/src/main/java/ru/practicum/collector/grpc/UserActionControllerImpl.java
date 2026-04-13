package ru.practicum.collector.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.collector.kafka.UserActionProducer;
import ru.practicum.collector.mapper.UserActionMapper;
import ru.practicum.grpc.stats.collector.UserActionControllerGrpc;
import ru.practicum.grpc.stats.action.UserActionProto;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserActionControllerImpl extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final UserActionProducer userActionProducer;

    @Override
    public void collectUserAction(UserActionProto request, StreamObserver<Empty> responseObserver) {
        log.info("Received user action: userId={}, eventId={}, actionType={}, timestamp={}",
                request.getUserId(),
                request.getEventId(),
                request.getActionType(),
                request.getTimestamp());

        try {
            var avroMessage = UserActionMapper.toAvro(request);
            userActionProducer.send(avroMessage);

            log.info("Successfully sent to Kafka: {}", avroMessage);

            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error processing user action", e);
            responseObserver.onError(e);
        }
    }
}
