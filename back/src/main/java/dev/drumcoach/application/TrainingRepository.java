package dev.drumcoach.application;

import java.util.List;
import java.util.Optional;

import dev.drumcoach.domain.Training;

/**
 * Port de persistencia de {@link Training}, implementado por {@code infra} (Spring Data
 * JDBC). A camada {@code application} so conhece esta interface - nunca depende
 * diretamente de Spring Data/JDBC.
 */
public interface TrainingRepository {

	Training save(Training training);

	List<Training> findAll();

	List<Training> findByGoalId(long goalId);

	Optional<Training> findById(long id);
}
