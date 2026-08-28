package dev.drumcoach.presentation.web;

import tools.jackson.databind.JsonNode;

import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.VideoSourceType;

/**
 * Corpo de {@code PATCH /api/exercises/{id}}. Todo campo ausente/{@code null} mantem o
 * valor atual. {@code pattern} e um objeto JSON e substitui o padrao inteiro (nao e um
 * diff). {@code kind} e opcional e serve apenas para detectar uma tentativa de troca de
 * tipo (rejeitada com 400) - o tipo do exercicio e imutavel.
 */
public record UpdateExerciseRequest(ExerciseKind kind, String name, String exerciseType, String howToExecute,
		JsonNode pattern, Integer targetBpm, Integer targetDurationSeconds, VideoSourceType videoSourceType,
		String videoUrl, String videoFilePath, Integer orderIndex) {
}
