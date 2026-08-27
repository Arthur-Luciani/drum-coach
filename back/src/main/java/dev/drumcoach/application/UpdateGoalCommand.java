package dev.drumcoach.application;

import dev.drumcoach.domain.GoalStatus;

/**
 * Comando de entrada para {@link UpdateGoalUseCase} (PATCH parcial). Campos {@code null}
 * mantem o valor atual. {@code inFocus} so tem efeito quando {@code true} (marca esta
 * meta como foco, desfocando as demais na mesma transacao); {@code null} ou {@code false}
 * nao mexem no foco atual - nao existe um jeito de "desfocar todas" isolado (ver ADR-0009
 * e ADR-0008: o usuario troca de foco escolhendo outra meta).
 */
public record UpdateGoalCommand(long id, GoalStatus status, String description, Boolean inFocus) {
}
