package ru.practicum.compilation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.model.Compilation;

@Mapper(componentModel = "spring")
public interface CompilationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "eventIds", source = "events")
    @Mapping(target = "pinned", defaultValue = "false")
    Compilation toEntity(NewCompilationDto newCompilationDto);

    @Mapping(target = "events", ignore = true)
    CompilationDto toDto(Compilation compilation);
}