package dev.drumcoach.presentation.web;

import tools.jackson.databind.JsonNode;

import dev.drumcoach.domain.ExerciseKind;

/**
 * Corpo de {@code PATCH /api/exercises/{id}}. Campos ausentes/{@code null} mantem o valor
 * atual. {@code pattern} e um objeto JSON. {@code kind} e opcional e serve apenas para
 * detectar uma tentativa de troca de tipo (rejeitada com 400) - o tipo do exercicio e
 * imutavel.
 */
public record UpdateExerciseRequest(ExerciseKind kind, JsonNode pattern, String howToExecute) {
}
