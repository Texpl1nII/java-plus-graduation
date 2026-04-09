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
    @Mapping(target = "rating", ignore = true)  // ← ДОБАВИТЬ (новое поле)
    Event toEntity(NewEventDto newEventDto);

    @Mapping(target = "id", ignore = true)
    Location toLocation(LocationDto locationDto);

    LocationDto toLocationDto(Location location);

    // toFullDto: category и initiator будут заполняться в Assembler
    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "rating", ignore = true)      // ← ДОБАВИТЬ (будет из Analyzer или из БД)
    @Mapping(target = "category", ignore = true)    // оставляем, заполняется в Assembler
    @Mapping(target = "initiator", ignore = true)   // оставляем, заполняется в Assembler
    EventFullDto toFullDto(Event event);

    // toShortDto: category и initiator будут заполняться в Assembler
    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "rating", ignore = true)      // ← ДОБАВИТЬ
    @Mapping(target = "category", ignore = true)    // оставляем, заполняется в Assembler
    @Mapping(target = "initiator", ignore = true)   // оставляем, заполняется в Assembler
    EventShortDto toShortDto(Event event);
}