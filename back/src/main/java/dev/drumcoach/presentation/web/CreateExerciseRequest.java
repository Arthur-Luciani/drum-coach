package dev.drumcoach.presentation.web;

import tools.jackson.databind.JsonNode;

import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.VideoSourceType;

/**
 * Corpo de {@code POST /api/trainings/{trainingId}/exercises}. {@code kind} e obrigatorio
 * ({@code TOCA_JUNTO} | {@code TRANSCRICAO}). {@code pattern} e o documento do padrao
 * tocavel como objeto JSON (nao string escapada) - so faz sentido em {@code TOCA_JUNTO}.
 */
public record CreateExerciseRequest(String name, String exerciseType, ExerciseKind kind, String howToExecute,
		JsonNode pattern, Integer targetBpm, Integer targetDurationSeconds, VideoSourceType videoSourceType,
		String videoUrl, String videoFilePath, int orderIndex) {
}
