package dev.drumcoach.infra.persistence;

import dev.drumcoach.domain.Goal;

/** Mapeamento domain.Goal <-> infra.persistence.GoalEntity, so via construtor/factory. */
final class GoalMapper {

	private GoalMapper() {
	}

	static GoalEntity toEntity(Goal goal) {
		return new GoalEntity(goal.id(), goal.title(), goal.description(), goal.targetDate(), goal.status(),
				goal.targetMetric(), goal.inFocus(), goal.createdBy(), goal.lastModifiedBy(), goal.createdAt(),
				goal.updatedAt());
	}

	static Goal toDomain(GoalEntity entity) {
		return Goal.reconstruct(entity.id(), entity.title(), entity.description(), entity.targetDate(),
				entity.status(), entity.targetMetric(), entity.inFocus(), entity.createdBy(), entity.lastModifiedBy(),
				entity.createdAt(), entity.updatedAt());
	}
}
