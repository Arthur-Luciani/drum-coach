package dev.drumcoach.presentation.web;

import java.time.Instant;
import java.time.LocalDate;

import dev.drumcoach.domain.Goal;
import dev.drumcoach.domain.GoalStatus;
import dev.drumcoach.domain.Origin;

/** DTO de saida de {@code Goal} para a API REST. */
public record GoalResponse(Long id, String title, String description, LocalDate targetDate, GoalStatus status,
		String targetMetric, boolean inFocus, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
		Instant updatedAt) {

	public static GoalResponse from(Goal goal) {
		return new GoalResponse(goal.id(), goal.title(), goal.description(), goal.targetDate(), goal.status(),
				goal.targetMetric(), goal.inFocus(), goal.createdBy(), goal.lastModifiedBy(), goal.createdAt(),
				goal.updatedAt());
	}
}
