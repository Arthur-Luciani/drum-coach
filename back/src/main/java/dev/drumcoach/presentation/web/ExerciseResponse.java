package dev.drumcoach.presentation.web;

import java.time.Instant;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.ExercisePassage;
import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.VideoSourceType;

/**
 * DTO de saida de {@code Exercise} para a API REST. {@code pattern} volta como objeto JSON
 * (nao string escapada); {@code passages} vem preenchida nas respostas que carregam o
 * exercicio inteiro ({@code GET /api/exercises/{id}}, {@code PATCH}) e vazia nas listagens.
 */
public record ExerciseResponse(Long id, Long trainingId, String name, String exerciseType, ExerciseKind kind,
		String howToExecute, JsonNode pattern, Integer targetBpm, Integer targetDurationSeconds,
		VideoSourceType videoSourceType, String videoUrl, String videoFilePath, int orderIndex, Origin createdBy,
		Origin lastModifiedBy, Instant createdAt, Instant updatedAt, List<MarkedPassageResponse> passages) {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static ExerciseResponse from(Exercise exercise) {
		return from(exercise, List.of());
	}

	public static ExerciseResponse from(Exercise exercise, List<ExercisePassage> passages) {
		return new ExerciseResponse(exercise.id(), exercise.trainingId(), exercise.name(), exercise.exerciseType(),
				exercise.kind(), exercise.howToExecute(), parsePattern(exercise.pattern()), exercise.targetBpm(),
				exercise.targetDurationSeconds(), exercise.videoSourceType(), exercise.videoUrl(),
				exercise.videoFilePath(), exercise.orderIndex(), exercise.createdBy(), exercise.lastModifiedBy(),
				exercise.createdAt(), exercise.updatedAt(),
				passages.stream().map(MarkedPassageResponse::from).toList());
	}

	private static JsonNode parsePattern(String pattern) {
		if (pattern == null) {
			return null;
		}
		try {
			return MAPPER.readTree(pattern);
		}
		catch (JacksonException e) {
			// pattern persistido sempre passou por DrumPattern - se chegou aqui invalido, e bug.
			throw new IllegalStateException("pattern persistido invalido: " + e.getMessage(), e);
		}
	}
}
