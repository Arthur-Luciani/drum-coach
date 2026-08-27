package dev.drumcoach.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Goal;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: criar uma meta. A origem da escrita (USER|CLAUDE) e resolvida via
 * {@link OriginProvider} - nunca recebida do chamador (controller REST ou tool MCP).
 */
@Component
public class CreateGoalUseCase {

	private final GoalRepository goalRepository;
	private final OriginProvider originProvider;

	public CreateGoalUseCase(GoalRepository goalRepository, OriginProvider originProvider) {
		this.goalRepository = goalRepository;
		this.originProvider = originProvider;
	}

	public Goal execute(CreateGoalCommand command) {
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		Goal goal = Goal.createNew(command.title(), command.description(), command.targetDate(),
				command.targetMetric(), origin, now);
		return goalRepository.save(goal);
	}
}
