package dev.drumcoach.infra.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.ExecutionExerciseLog}, mapeado
 * via Spring Data JDBC para a tabela {@code execution_exercise_log}. Tabela filha sem
 * auditoria propria (ver ADR-0005). Nunca e exposto fora de {@code infra} - o
 * domain.ExecutionExerciseLog e mapeado de/para esta classe por {@link ExecutionMapper}.
 */
@Table("execution_exercise_log")
public record ExecutionExerciseLogEntity(@Id Long id, Long executionId, Long exerciseId, Integer achievedBpm,
		Integer actualDurationSeconds, String notes, Instant createdAt) {
}
