package dev.drumcoach.presentation.web;

import dev.drumcoach.application.TrainingWithProgress;

/** Progresso de um treino de uma meta: execucoes registradas vs. meta de repeticoes. */
public record TrainingProgressResponse(Long trainingId, String name, Integer targetRepetitions,
		long completedCount) {

	public static TrainingProgressResponse from(TrainingWithProgress withProgress) {
		return new TrainingProgressResponse(withProgress.training().id(), withProgress.training().name(),
				withProgress.training().targetRepetitions(), withProgress.completedCount());
	}
}
