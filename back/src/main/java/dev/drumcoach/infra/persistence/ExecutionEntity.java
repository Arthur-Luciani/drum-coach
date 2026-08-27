package dev.drumcoach.infra.persistence;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import dev.drumcoach.domain.Origin;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.Execution} (raiz do agregado -
 * sem os logs, que sao mapeados separadamente por {@link ExecutionExerciseLogEntity}),
 * mapeado via Spring Data JDBC para a tabela {@code execution}. Nunca e exposto fora de
 * {@code infra} - o domain.Execution e mapeado de/para esta classe por
 * {@link ExecutionMapper}.
 */
@Table("execution")
public record ExecutionEntity(@Id Long id, Long trainingId, LocalDate executionDate, Integer actualDurationMinutes,
		String feeling, String generalNotes, Long goalId, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
		Instant updatedAt) {
}
