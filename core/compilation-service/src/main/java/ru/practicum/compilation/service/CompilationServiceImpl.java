package ru.practicum.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.compilation.client.EventClient;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.EventShortDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationDto;
import ru.practicum.compilation.mapper.CompilationMapper;
import ru.practicum.compilation.model.Compilation;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.exception.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventClient eventClient;
    private final CompilationMapper compilationMapper;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newCompilationDto) {
        log.info("Creating new compilation with title: {}", newCompilationDto.getTitle());

        Compilation compilation = compilationMapper.toEntity(newCompilationDto);
        if (newCompilationDto.getEvents() != null && !newCompilationDto.getEvents().isEmpty()) {
            compilation.setEventIds(new HashSet<>(newCompilationDto.getEvents()));
        }

        Compilation savedCompilation = compilationRepository.save(compilation);

        return toDtoWithEvents(savedCompilation);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Deleting compilation with id: {}", compId);

        getCompilationByIdOrThrow(compId);

        compilationRepository.deleteById(compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationDto updateCompilationDto) {
        log.info("Updating compilation with id: {}", compId);

        Compilation compilation = getCompilationByIdOrThrow(compId);

        if (updateCompilationDto.getTitle() != null) {
            compilation.setTitle(updateCompilationDto.getTitle());
        }

        if (updateCompilationDto.getPinned() != null) {
            compilation.setPinned(updateCompilationDto.getPinned());
        }

        if (updateCompilationDto.getEvents() != null) {
            compilation.setEventIds(new HashSet<>(updateCompilationDto.getEvents()));
        }

        Compilation updatedCompilation = compilationRepository.save(compilation);
        return toDtoWithEvents(updatedCompilation);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, Pageable pageable) {
        log.info("Getting compilations with pinned={}", pinned);

        List<Compilation> compilations;
        if (pinned != null) {
            compilations = compilationRepository.findAllByPinned(pinned, pageable).getContent();
        } else {
            compilations = compilationRepository.findAll(pageable).getContent();
        }

        return compilations.stream()
                .map(this::toDtoWithEvents)
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        log.info("Getting compilation with id: {}", compId);

        Compilation compilation = getCompilationByIdOrThrow(compId);
        return toDtoWithEvents(compilation);
    }

    private Compilation getCompilationByIdOrThrow(Long compId) {
        return compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
    }

    private CompilationDto toDtoWithEvents(Compilation compilation) {
        CompilationDto dto = compilationMapper.toDto(compilation);

        if (compilation.getEventIds() != null && !compilation.getEventIds().isEmpty()) {
            try {
                List<EventShortDto> events = eventClient.getEventsByIds(new ArrayList<>(compilation.getEventIds()));
                dto.setEvents(events);
            } catch (Exception e) {
                log.error("Error fetching events for compilation {}", compilation.getId(), e);
                dto.setEvents(Collections.emptyList());
            }
        } else {
            dto.setEvents(Collections.emptyList());
        }

        return dto;
    }
}