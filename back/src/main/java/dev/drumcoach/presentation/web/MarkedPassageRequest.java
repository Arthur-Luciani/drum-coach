package dev.drumcoach.presentation.web;

/**
 * Corpo de {@code POST /api/exercises/{exerciseId}/passages}. {@code fromSeconds} e
 * obrigatorio; {@code toSeconds} e {@code label} sao opcionais.
 */
public record MarkedPassageRequest(Integer fromSeconds, Integer toSeconds, String label) {
}
