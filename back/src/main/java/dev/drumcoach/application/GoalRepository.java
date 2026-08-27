package dev.drumcoach.application;

import java.util.List;
import java.util.Optional;

import dev.drumcoach.domain.Goal;

/**
 * Port de persistencia de {@link Goal}, implementado por {@code infra} (Spring Data
 * JDBC). A camada {@code application} so conhece esta interface - nunca depende
 * diretamente de Spring Data/JDBC.
 */
public interface GoalRepository {

	Goal save(Goal goal);

	List<Goal> findAll();

	Optional<Goal> findById(long id);
}
