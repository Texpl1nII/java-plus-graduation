package ru.practicum.ewm.stat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.ewm.stat.mapper.EndpointHitMapper;
import ru.practicum.ewm.stat.model.EndpointHit;
import ru.practicum.ewm.stat.repository.EndpointHitRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {
    private final EndpointHitRepository repository;
    private final EndpointHitMapper mapper;

    @Override
    @Transactional
    public EndpointHitDto saveHit(EndpointHitDto dto) {
        EndpointHit entity = mapper.toEntity(dto);
        EndpointHit saved = repository.save(entity);
        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Старт должен быть до окончания");
        }

        if (uris == null || uris.isEmpty()) {
            if (unique != null && unique) {
                return repository.findUniqueStats(start, end);
            } else {
                return repository.findStats(start, end);
            }
        }

        if (unique != null && unique) {
            return repository.findUniqueStats(start, end, uris);
        } else {
            return repository.findStats(start, end, uris);
        }
    }
}

