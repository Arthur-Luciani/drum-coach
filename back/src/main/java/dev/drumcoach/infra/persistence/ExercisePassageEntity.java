package dev.drumcoach.infra.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.ExercisePassage}, mapeado via
 * Spring Data JDBC para a tabela {@code exercise_passage}. Tabela filha sem auditoria
 * propria (ver ADR-0005). Nunca e exposto fora de {@code infra} - o domain.ExercisePassage
 * e mapeado de/para esta classe por {@link ExerciseMapper}.
 */
@Table("exercise_passage")
public record ExercisePassageEntity(@Id Long id, Long exerciseId, int fromSeconds, Integer toSeconds, String label,
		Instant createdAt) {
}
