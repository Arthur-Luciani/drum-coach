package dev.drumcoach.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.Training;

/**
 * Caso de uso: criar um treino (vinculado direto a uma meta ou avulso). A origem da
 * escrita (USER|CLAUDE) e resolvida via {@link OriginProvider} - nunca recebida do
 * chamador (controller REST ou tool MCP).
 */
@Component
public class CreateTrainingUseCase {

	private final TrainingRepository trainingRepository;
	private final OriginProvider originProvider;

	public CreateTrainingUseCase(TrainingRepository trainingRepository, OriginProvider originProvider) {
		this.trainingRepository = trainingRepository;
		this.originProvider = originProvider;
	}

	public Training execute(CreateTrainingCommand command) {
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		Training training = Training.createNew(command.goalId(), command.name(), command.description(),
				command.targetDurationMinutes(), command.targetRepetitions(), command.orderIndex(), origin, now);
		return trainingRepository.save(training);
	}
}
