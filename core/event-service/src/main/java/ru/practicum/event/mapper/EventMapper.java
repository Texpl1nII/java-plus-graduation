package ru.practicum.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.event.dto.EventFullDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.dto.LocationDto;
import ru.practicum.event.dto.NewEventDto;
import ru.practicum.event.model.Event;
import ru.practicum.event.model.Location;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "createdOn", ignore = true)
    @Mapping(target = "publishedOn", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "initiatorId", ignore = true)  // ← initiatorId не из DTO, устанавливается отдельно
    @Mapping(target = "categoryId", source = "categoryId")
    Event toEntity(NewEventDto newEventDto);

    @Mapping(target = "id", ignore = true)
    Location toLocation(LocationDto locationDto);

    LocationDto toLocationDto(Location location);

    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "initiatorId", source = "initiatorId")
    EventFullDto toFullDto(Event event);

    @Mapping(target = "confirmedRequests", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "categoryId", source = "categoryId")
    @Mapping(target = "initiatorId", source = "initiatorId")
    EventShortDto toShortDto(Event event);
}