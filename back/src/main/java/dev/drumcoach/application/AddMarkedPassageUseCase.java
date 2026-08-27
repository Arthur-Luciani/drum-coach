package dev.drumcoach.application;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.ExercisePassage;

/**
 * Caso de uso: adicionar um trecho marcado a um exercicio (tipicamente {@code
 * TRANSCRICAO}). Colecao filha sem auditoria propria - a origem e a do exercicio pai (ver
 * ADR-0005), entao aqui nao ha {@link OriginProvider}.
 */
@Component
public class AddMarkedPassageUseCase {

	private final ExerciseRepository exerciseRepository;

	public AddMarkedPassageUseCase(ExerciseRepository exerciseRepository) {
		this.exerciseRepository = exerciseRepository;
	}

	public ExercisePassage execute(AddMarkedPassageCommand command) {
		exerciseRepository.findById(command.exerciseId())
			.orElseThrow(() -> new NoSuchElementException("Exercise nao encontrado: " + command.exerciseId()));
		int fromSeconds = Objects.requireNonNull(command.fromSeconds(), "fromSeconds must not be null");
		ExercisePassage passage = ExercisePassage.createNew(fromSeconds, command.toSeconds(), command.label(),
				Instant.now());
		return exerciseRepository.addPassage(command.exerciseId(), passage);
	}
}
