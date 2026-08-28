package dev.drumcoach.application;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.drumcoach.domain.Exercise;

/**
 * Caso de uso: apagar um exercicio e seus trechos marcados ({@code exercise_passage}) em
 * cascata, numa unica transacao (filho-primeiro).
 *
 * Antes de apagar, verifica se ha algum {@code execution_exercise_log} referenciando o
 * exercicio: se houver, lanca {@link IllegalStateException} (-&gt; 409) - esse log e parte
 * do historico real de uma execucao. {@link NoSuchElementException} se o exercicio nao
 * existe (-&gt; 404).
 */
@Component
public class DeleteExerciseUseCase {

	private final ExerciseRepository exerciseRepository;
	private final ExecutionRepository executionRepository;

	public DeleteExerciseUseCase(ExerciseRepository exerciseRepository, ExecutionRepository executionRepository) {
		this.exerciseRepository = exerciseRepository;
		this.executionRepository = executionRepository;
	}

	@Transactional
	public void execute(long id) {
		Exercise current = exerciseRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("Exercise nao encontrado: " + id));
		long logs = executionRepository.countExerciseLogsByExerciseId(current.id());
		if (logs > 0) {
			throw new IllegalStateException(logs + " log(s) de execucao referenciam este exercicio - remova as "
					+ "execucoes primeiro ou mantenha o exercicio");
		}
		exerciseRepository.deleteById(current.id());
	}
}
