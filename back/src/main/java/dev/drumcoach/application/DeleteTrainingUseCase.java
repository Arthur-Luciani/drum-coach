package dev.drumcoach.application;

import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.drumcoach.domain.Training;

/**
 * Caso de uso: apagar um treino e, em cascata, seus exercicios e os trechos marcados
 * ({@code exercise_passage}) deles - tudo numa unica transacao, filho-primeiro.
 *
 * Antes de apagar, verifica se ha {@code execution} vinculada ao treino: se houver, lanca
 * {@link IllegalStateException} (-&gt; 409) em vez de apagar - as execucoes sao o historico
 * real de pratica e nao devem sumir junto com o template. {@link NoSuchElementException} se
 * o treino nao existe (-&gt; 404).
 */
@Component
public class DeleteTrainingUseCase {

	private final TrainingRepository trainingRepository;
	private final ExerciseRepository exerciseRepository;
	private final ExecutionRepository executionRepository;

	public DeleteTrainingUseCase(TrainingRepository trainingRepository, ExerciseRepository exerciseRepository,
			ExecutionRepository executionRepository) {
		this.trainingRepository = trainingRepository;
		this.exerciseRepository = exerciseRepository;
		this.executionRepository = executionRepository;
	}

	@Transactional
	public void execute(long id) {
		Training current = trainingRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("Training nao encontrado: " + id));
		long executions = executionRepository.countByTrainingId(current.id());
		if (executions > 0) {
			throw new IllegalStateException(executions + " execucao(oes) registrada(s) neste treino - remova as "
					+ "execucoes primeiro ou mantenha o treino");
		}
		exerciseRepository.deleteByTrainingId(current.id());
		trainingRepository.deleteById(current.id());
	}
}
