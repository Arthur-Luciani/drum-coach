package dev.drumcoach.application;

/**
 * Comando de entrada para {@link AddMarkedPassageUseCase}. {@code fromSeconds} e
 * obrigatorio; {@code toSeconds} nulo = marcacao pontual; {@code label} opcional.
 */
public record AddMarkedPassageCommand(long exerciseId, Integer fromSeconds, Integer toSeconds, String label) {
}
