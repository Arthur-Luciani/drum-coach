package dev.drumcoach.presentation.web;

import java.time.Instant;

import dev.drumcoach.domain.ExercisePassage;

/** DTO de saida de um trecho marcado ({@code exercise_passage}) para a API REST. */
public record MarkedPassageResponse(Long id, int fromSeconds, Integer toSeconds, String label, Instant createdAt) {

	public static MarkedPassageResponse from(ExercisePassage passage) {
		return new MarkedPassageResponse(passage.id(), passage.fromSeconds(), passage.toSeconds(), passage.label(),
				passage.createdAt());
	}
}
