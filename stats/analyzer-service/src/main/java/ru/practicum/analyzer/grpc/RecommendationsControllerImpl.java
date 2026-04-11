package ru.practicum.analyzer.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.practicum.analyzer.service.PredictionService;
import ru.practicum.analyzer.service.RecommendationService;
import ru.practicum.ewm.stats.proto.*;

import java.util.List;
import java.util.Map;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RecommendationsControllerImpl extends RecommendationsControllerGrpc.RecommendationsControllerImplBase {

    private final RecommendationService recommendationService;
    private final PredictionService predictionService;

    @Override
    public void getRecommendationsForUser(UserPredictionsRequest request,
                                          StreamObserver<RecommendedEvent> responseObserver) {
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        log.info("gRPC call: getRecommendationsForUser userId={}, maxResults={}", userId, maxResults);

        try {
            List<Map.Entry<Long, Double>> recommendations =
                    recommendationService.getRecommendationsForUser(userId, maxResults);

            for (Map.Entry<Long, Double> entry : recommendations) {
                double predictedRating = predictionService.predictRating(userId, entry.getKey());

                RecommendedEvent event = RecommendedEvent.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(predictedRating)
                        .build();

                responseObserver.onNext(event);
            }

            responseObserver.onCompleted();
            log.info("Sent {} recommendations for userId={}", recommendations.size(), userId);

        } catch (Exception e) {
            log.error("Error getting recommendations for userId={}", userId, e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getSimilarEvents(SimilarEventsRequest request,
                                 StreamObserver<RecommendedEvent> responseObserver) {
        long eventId = request.getEventId();
        long userId = request.getUserId();
        int maxResults = request.getMaxResults();

        log.info("gRPC call: getSimilarEvents eventId={}, userId={}, maxResults={}",
                eventId, userId, maxResults);

        try {
            List<Map.Entry<Long, Double>> similarEvents =
                    recommendationService.getSimilarEvents(eventId, userId, maxResults);

            for (Map.Entry<Long, Double> entry : similarEvents) {
                RecommendedEvent event = RecommendedEvent.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(entry.getValue())
                        .build();

                responseObserver.onNext(event);
            }

            responseObserver.onCompleted();
            log.info("Sent {} similar events for eventId={}", similarEvents.size(), eventId);

        } catch (Exception e) {
            log.error("Error getting similar events for eventId={}", eventId, e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getInteractionsCount(InteractionsCountRequest request,
                                     StreamObserver<RecommendedEvent> responseObserver) {
        List<Long> eventIds = request.getEventIdsList();

        log.info("gRPC call: getInteractionsCount for {} events", eventIds.size());

        try {
            Map<Long, Double> interactionsCount =
                    recommendationService.getInteractionsCount(eventIds);

            for (Map.Entry<Long, Double> entry : interactionsCount.entrySet()) {
                RecommendedEvent event = RecommendedEvent.newBuilder()
                        .setEventId(entry.getKey())
                        .setScore(entry.getValue())
                        .build();

                responseObserver.onNext(event);
            }

            responseObserver.onCompleted();
            log.info("Sent interactions count for {} events", interactionsCount.size());

        } catch (Exception e) {
            log.error("Error getting interactions count", e);
            responseObserver.onError(e);
        }
    }
}
