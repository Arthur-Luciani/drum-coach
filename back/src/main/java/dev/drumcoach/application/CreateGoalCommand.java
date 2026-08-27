package dev.drumcoach.application;

import java.time.LocalDate;

/** Comando de entrada para {@link CreateGoalUseCase}. */
public record CreateGoalCommand(String title, String description, LocalDate targetDate, String targetMetric) {
}
