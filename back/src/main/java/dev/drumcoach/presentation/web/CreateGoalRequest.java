package dev.drumcoach.presentation.web;

import java.time.LocalDate;

/** Corpo de {@code POST /api/goals}. */
public record CreateGoalRequest(String title, String description, LocalDate targetDate, String targetMetric) {
}
