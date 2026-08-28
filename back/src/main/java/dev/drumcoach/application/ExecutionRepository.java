package dev.drumcoach.application;

import java.util.List;
import java.util.Optional;

import dev.drumcoach.domain.Execution;

/**
 * Port de persistencia de {@link Execution} (agregado com seus {@code
 * ExecutionExerciseLog}), implementado por {@code infra} (Spring Data JDBC). A camada
 * {@code application} so conhece esta interface - nunca depende diretamente de Spring
 * Data/JDBC.
 */
public interface ExecutionRepository {

	/** Persiste a execucao e seus logs de exercicio numa unica operacao. */
	Execution save(Execution execution);

	List<Execution> findAll();

	List<Execution> findByTrainingId(long trainingId);

	/** Execucoes vinculadas diretamente a meta informada ({@code execution.goal_id}). */
	List<Execution> findByGoalId(long goalId);

	Optional<Execution> findById(long id);

	long countByTrainingId(long trainingId);

	/** Quantos {@code execution_exercise_log} referenciam o exercicio informado. */
	long countExerciseLogsByExerciseId(long exerciseId);
}
