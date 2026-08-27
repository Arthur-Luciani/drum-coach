package dev.drumcoach.application;

import java.util.List;
import java.util.Optional;

import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.ExercisePassage;

/**
 * Port de persistencia de {@link Exercise}, implementado por {@code infra} (Spring Data
 * JDBC). A camada {@code application} so conhece esta interface - nunca depende
 * diretamente de Spring Data/JDBC.
 *
 * Os {@link ExercisePassage} (trechos marcados de uma transcricao) sao uma colecao filha
 * do exercicio, manipulada aqui de forma incremental (add/list/delete), no mesmo padrao
 * das outras colecoes filhas do projeto (ver ADR-0005/ADR-0011).
 */
public interface ExerciseRepository {

	Exercise save(Exercise exercise);

	List<Exercise> findByTrainingId(long trainingId);

	Optional<Exercise> findById(long id);

	/** Trechos marcados do exercicio, ordenados por {@code fromSeconds}. */
	List<ExercisePassage> findPassagesByExerciseId(long exerciseId);

	/** Insere um novo trecho marcado no exercicio e devolve-o com id/createdAt. */
	ExercisePassage addPassage(long exerciseId, ExercisePassage passage);

	/**
	 * Remove um trecho marcado do exercicio. Lanca {@link java.util.NoSuchElementException}
	 * se o trecho nao existir ou nao pertencer ao exercicio informado.
	 */
	void deletePassage(long exerciseId, long passageId);
}
