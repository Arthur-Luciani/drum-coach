package dev.drumcoach.application;

import java.util.List;

import dev.drumcoach.domain.Goal;

/**
 * Resultado de {@link GetGoalDetailUseCase}: a meta com os treinos que pertencem direto a
 * ela (ver ADR-0009) e o progresso de cada um. Substitui o antigo
 * {@code TrainingPlanDetail} - nao existe mais um Plano entre Meta e Treino.
 */
public record GoalDetail(Goal goal, List<TrainingWithProgress> trainings) {
}
