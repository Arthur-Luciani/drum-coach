package dev.drumcoach.application;

import dev.drumcoach.domain.Training;

/**
 * Resultado composto: um {@link Training} de uma meta junto com sua contagem de
 * {@code Execution} registradas ({@code completedCount}), usado pela visao de progresso
 * de {@link GetGoalDetailUseCase}.
 */
public record TrainingWithProgress(Training training, long completedCount) {
}
