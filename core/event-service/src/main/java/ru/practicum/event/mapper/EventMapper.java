package ru.practicum.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.event.dto.*;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.Location;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdOn", ignore = true)
    @Mapping(target = "publishedOn", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "initiatorId", ignore = true)
    @Mapping(target = "categoryId", source = "categoryId")
    Event toEntity(NewEventDto newEventDto);

    @Mapping(target = "id", ignore = true)
    Location toLocation(LocationDto locationDto);

    LocationDto toLocationDto(Location location);

    // Убираем ignore для category и initiator
    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
    // @Mapping(target = "category", ignore = true)  // УДАЛИТЬ ЭТУ СТРОКУ
    // @Mapping(target = "initiator", ignore = true) // УДАЛИТЬ ЭТУ СТРОКУ
    EventFullDto toFullDto(Event event);

    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
        // @Mapping(target = "category", ignore = true)  // УДАЛИТЬ ЭТУ СТРОКУ
        // @Mapping(target = "initiator", ignore = true) // УДАЛИТЬ ЭТУ СТРОКУ
    EventShortDto toShortDto(Event event);
}