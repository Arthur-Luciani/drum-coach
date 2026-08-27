package dev.drumcoach.infra.persistence;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

/** Repositorio tecnico Spring Data JDBC - detalhe de implementacao de {@code infra}. */
interface SpringDataExecutionRepository extends CrudRepository<ExecutionEntity, Long> {

	List<ExecutionEntity> findByTrainingId(long trainingId);

	long countByTrainingId(long trainingId);

	/** Execucoes vinculadas diretamente a meta informada ({@code execution.goal_id}). */
	List<ExecutionEntity> findByGoalId(long goalId);
}
