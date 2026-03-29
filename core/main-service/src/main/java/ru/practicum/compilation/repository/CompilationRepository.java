package ru.practicum.compilation.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.compilation.model.Compilation;

public interface CompilationRepository extends JpaRepository<Compilation, Long> {

    Page<Compilation> findAllByPinned(Boolean pinned, Pageable pageable);

    @Query("SELECT c FROM Compilation c WHERE " +
            "(:pinned IS NULL OR c.pinned = :pinned)")
    Page<Compilation> findAllByPinnedFilter(@Param("pinned") Boolean pinned, Pageable pageable);
}