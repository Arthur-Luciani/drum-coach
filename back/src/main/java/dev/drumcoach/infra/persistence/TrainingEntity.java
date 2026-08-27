package dev.drumcoach.infra.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import dev.drumcoach.domain.Origin;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.Training}, mapeado via Spring
 * Data JDBC para a tabela {@code training}. Nunca e exposto fora de {@code infra} - o
 * domain.Training e mapeado de/para esta classe por {@link TrainingMapper}.
 */
@Table("training")
public record TrainingEntity(@Id Long id, Long goalId, String name, String description, int targetDurationMinutes,
		Integer targetRepetitions, int orderIndex, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
		Instant updatedAt) {
}
