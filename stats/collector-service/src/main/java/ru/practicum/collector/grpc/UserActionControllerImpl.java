package ru.practicum.collector.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.collector.kafka.UserActionProducer;
import ru.practicum.ewm.stats.avro.UserActionAvro;
import ru.practicum.ewm.stats.proto.UserActionControllerGrpc;
import ru.practicum.ewm.stats.proto.UserActionRequest;
import ru.practicum.ewm.stats.proto.Empty;

import java.time.Instant;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class UserActionControllerImpl extends UserActionControllerGrpc.UserActionControllerImplBase {

    private final UserActionProducer userActionProducer;

    @Override
    public void collectUserAction(UserActionRequest request, StreamObserver<Empty> responseObserver) {
        try {
            log.info("Received user action: userId={}, eventId={}, actionType={}, timestamp={}",
                    request.getUserId(),
                    request.getEventId(),
                    request.getActionType(),
                    request.getTimestamp());

            UserActionAvro userActionAvro = UserActionAvro.newBuilder()
                    .setUserId(request.getUserId())
                    .setEventId(request.getEventId())
                    .setActionType(mapActionType(request.getActionType()))
                    .setTimestamp(Instant.ofEpochSecond(
                            request.getTimestamp().getSeconds(),
                            request.getTimestamp().getNanos()
                    ))
                    .build();

            userActionProducer.send(userActionAvro);

            responseObserver.onNext(Empty.newBuilder().build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error processing user action", e);
            responseObserver.onError(e);
        }
    }

    private ru.practicum.ewm.stats.avro.ActionTypeAvro mapActionType(
            ru.practicum.ewm.stats.proto.ActionTypeProto actionTypeProto) {

        switch (actionTypeProto) {
            case ACTION_VIEW:
                return ru.practicum.ewm.stats.avro.ActionTypeAvro.VIEW;
            case ACTION_REGISTER:
                return ru.practicum.ewm.stats.avro.ActionTypeAvro.REGISTER;
            case ACTION_LIKE:
                return ru.practicum.ewm.stats.avro.ActionTypeAvro.LIKE;
            default:
                throw new IllegalArgumentException("Unknown action type: " + actionTypeProto);
        }
    }
}