package dev.drumcoach.application;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

/**
 * Caso de uso: remover um trecho marcado de um exercicio. Falha com
 * {@link NoSuchElementException} (-&gt; 404) se o exercicio ou o trecho nao existirem, ou
 * se o trecho nao pertencer ao exercicio informado.
 */
@Component
public class DeleteMarkedPassageUseCase {

	private final ExerciseRepository exerciseRepository;

	public DeleteMarkedPassageUseCase(ExerciseRepository exerciseRepository) {
		this.exerciseRepository = exerciseRepository;
	}

	public void execute(long exerciseId, long passageId) {
		exerciseRepository.findById(exerciseId)
			.orElseThrow(() -> new NoSuchElementException("Exercise nao encontrado: " + exerciseId));
		exerciseRepository.deletePassage(exerciseId, passageId);
	}
}
