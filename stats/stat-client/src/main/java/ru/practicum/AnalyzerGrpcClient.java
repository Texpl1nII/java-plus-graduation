package ru.practicum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.practicum.stats.proto.*;

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

    /**
     * Получить рекомендации для пользователя
     * @param userId ID пользователя
     * @param maxResults максимальное количество результатов
     * @return список рекомендуемых мероприятий
     */
    public List<RecommendedEvent> getRecommendationsForUser(long userId, int maxResults) {
        try {
            UserPredictionsRequest request = UserPredictionsRequest.newBuilder()
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();

            Iterator<RecommendedEvent> iterator = analyzerStub.getRecommendationsForUser(request);
            return toList(iterator);
        } catch (Exception e) {
            log.error("Failed to get recommendations for user: {}", userId, e);
            return List.of();
        }
    }

    /**
     * Получить мероприятия, похожие на указанное
     * @param eventId ID мероприятия
     * @param userId ID пользователя (для исключения просмотренных)
     * @param maxResults максимальное количество результатов
     * @return список похожих мероприятий
     */
    public List<RecommendedEvent> getSimilarEvents(long eventId, long userId, int maxResults) {
        try {
            SimilarEventsRequest request = SimilarEventsRequest.newBuilder()
                    .setEventId(eventId)
                    .setUserId(userId)
                    .setMaxResults(maxResults)
                    .build();

            Iterator<RecommendedEvent> iterator = analyzerStub.getSimilarEvents(request);
            return toList(iterator);
        } catch (Exception e) {
            log.error("Failed to get similar events for eventId: {}", eventId, e);
            return List.of();
        }
    }

    /**
     * Получить сумму взаимодействий для мероприятий (рейтинг)
     * @param eventIds список ID мероприятий
     * @return список мероприятий с их рейтингом
     */
    public List<RecommendedEvent> getInteractionsCount(List<Long> eventIds) {
        try {
            InteractionsCountRequest.Builder builder = InteractionsCountRequest.newBuilder();
            for (Long eventId : eventIds) {
                builder.addEventIds(eventId);
            }

            Iterator<RecommendedEvent> iterator = analyzerStub.getInteractionsCount(builder.build());
            return toList(iterator);
        } catch (Exception e) {
            log.error("Failed to get interactions count for events: {}", eventIds, e);
            return List.of();
        }
    }

    private List<RecommendedEvent> toList(Iterator<RecommendedEvent> iterator) {
        Iterable<RecommendedEvent> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toList());
    }
}
