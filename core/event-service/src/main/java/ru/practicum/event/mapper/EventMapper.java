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
    @Mapping(target = "categoryId", source = "categoryId")  // ← ОСТАЕТСЯ для entity
    Event toEntity(NewEventDto newEventDto);

    @Mapping(target = "id", ignore = true)
    Location toLocation(LocationDto locationDto);

    LocationDto toLocationDto(Location location);

    // УБРАТЬ @Mapping для categoryId, так как теперь category - это объект
    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "category", ignore = true)  // ← ИГНОРИРУЕМ, заполним в сервисе
    @Mapping(target = "initiatorId", source = "initiatorId")
    EventFullDto toFullDto(Event event);

    // Аналогично для ShortDto
    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "category", ignore = true)  // ← ИГНОРИРУЕМ
    @Mapping(target = "initiatorId", source = "initiatorId")
    EventShortDto toShortDto(Event event);
}