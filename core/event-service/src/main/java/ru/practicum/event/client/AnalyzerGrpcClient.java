package ru.practicum.event.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.grpc.stats.analyzer.RecommendationsControllerGrpc;
import ru.practicum.grpc.stats.recommendation.UserPredictionsRequestProto;
import ru.practicum.grpc.stats.recommendation.SimilarEventsRequestProto;
import ru.practicum.grpc.stats.recommendation.InteractionsCountRequestProto;
import ru.practicum.grpc.stats.recommendation.RecommendedEventProto;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzerGrpcClient {

    @GrpcClient("analyzer-service")
    private RecommendationsControllerGrpc.RecommendationsControllerBlockingStub analyzerStub;

    public List<RecommendedEventProto> getRecommendationsForUser(long userId, int maxResults) {
        try {
            UserPredictionsRequestProto request = UserPredictionsRequestProto.newBuilder()
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();

            Iterator<RecommendedEventProto> iterator = analyzerStub.getRecommendationsForUser(request);
            return toList(iterator);
        } catch (Exception e) {
            log.error("Failed to get recommendations for user: {}", userId, e);
            return List.of();
        }
    }

    public List<RecommendedEventProto> getSimilarEvents(long eventId, long userId, int maxResults) {
        try {
            SimilarEventsRequestProto request = SimilarEventsRequestProto.newBuilder()
                    .setEventId(eventId)
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();

            Iterator<RecommendedEventProto> iterator = analyzerStub.getSimilarEvents(request);
            return toList(iterator);
        } catch (Exception e) {
            log.error("Failed to get similar events for eventId: {}", eventId, e);
            return List.of();
        }
    }

    public List<RecommendedEventProto> getInteractionsCount(List<Long> eventIds) {
        try {
            InteractionsCountRequestProto.Builder builder = InteractionsCountRequestProto.newBuilder();
            for (Long eventId : eventIds) {
                builder.addEventId(eventId);
            }

            Iterator<RecommendedEventProto> iterator = analyzerStub.getInteractionsCount(builder.build());
            return toList(iterator);
        } catch (Exception e) {
            log.error("Failed to get interactions count", e);
            return List.of();
        }
    }

    private List<RecommendedEventProto> toList(Iterator<RecommendedEventProto> iterator) {
        Iterable<RecommendedEventProto> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toList());
    }
}
