package dev.drumcoach.presentation.web;

import java.util.List;

import dev.drumcoach.application.GoalDetail;

/**
 * DTO de saida de {@code GET /api/goals/{id}}: a meta com os treinos que pertencem direto
 * a ela (ver ADR-0009 - nao existe mais um Plano entre Meta e Treino) e o progresso de
 * cada um.
 */
public record GoalDetailResponse(GoalResponse goal, List<TrainingProgressResponse> trainings) {

	public static GoalDetailResponse from(GoalDetail detail) {
		List<TrainingProgressResponse> trainings = detail.trainings().stream()
			.map(TrainingProgressResponse::from)
			.toList();
		return new GoalDetailResponse(GoalResponse.from(detail.goal()), trainings);
	}
}
