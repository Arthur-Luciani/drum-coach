package dev.drumcoach.presentation.web;

import dev.drumcoach.domain.GoalStatus;

/**
 * Corpo de {@code PATCH /api/goals/{id}}. Campos {@code null} mantem o valor atual.
 * {@code description} tambem serve como o "notes" de progresso da meta (o dominio nao
 * tem um campo de notas separado - a MCP tool {@code update_goal_progress} escreve aqui).
 * {@code inFocus: true} marca esta meta como foco do Dashboard, desfocando qualquer outra
 * meta atualmente em foco (mesma transacao) - ver ADR-0008/ADR-0009. Nao ha suporte a
 * {@code inFocus: false} isolado (o produto nao tem o conceito de "desfocar todas": voce
 * troca de foco escolhendo outra meta).
 */
public record UpdateGoalRequest(GoalStatus status, String description, Boolean inFocus) {
}
