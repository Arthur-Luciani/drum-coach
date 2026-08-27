package dev.drumcoach.infra.persistence;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.VideoSourceType;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.Exercise}, mapeado via Spring
 * Data JDBC para a tabela {@code exercise}. Nunca e exposto fora de {@code infra} - o
 * domain.Exercise e mapeado de/para esta classe por {@link ExerciseMapper}.
 */
@Table("exercise")
public record ExerciseEntity(@Id Long id, Long trainingId, String name, String exerciseType, ExerciseKind kind,
		String howToExecute, String pattern, Integer targetBpm, Integer targetDurationSeconds,
		VideoSourceType videoSourceType, String videoUrl, String videoFilePath, int orderIndex, Origin createdBy,
		Origin lastModifiedBy, Instant createdAt, Instant updatedAt) {
}
