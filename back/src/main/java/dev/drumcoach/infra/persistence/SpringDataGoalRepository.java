package dev.drumcoach.infra.persistence;

import org.springframework.data.repository.CrudRepository;

/** Repositorio tecnico Spring Data JDBC - detalhe de implementacao de {@code infra}. */
interface SpringDataGoalRepository extends CrudRepository<GoalEntity, Long> {
}
