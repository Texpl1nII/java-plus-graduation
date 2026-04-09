package ru.practicum.event.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Getter
@Setter
@ToString
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String annotation;

    @Column(nullable = false, length = 7000)
    private String description;

    @Column(name = "event_date", nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "created_on", nullable = false)
    private LocalDateTime createdOn;

    @Column(name = "published_on")
    private LocalDateTime publishedOn;

    @Column(nullable = false)
    private Boolean paid;

    @Column(name = "request_moderation", nullable = false)
    private Boolean requestModeration;

    @Column(name = "participant_limit", nullable = false)
    private Integer participantLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventState state;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "location_id", nullable = false)
    @ToString.Exclude
    private Location location;

    // НОВОЕ ПОЛЕ! рейтинг мероприятия (вместо views)
    @Column(nullable = false)
    private Double rating = 0.0;
}