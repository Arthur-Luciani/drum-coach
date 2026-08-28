package dev.drumcoach.application;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.Training;

/**
 * Caso de uso: atualizacao parcial de um treino (nome, descricao, duracao/repeticoes alvo,
 * meta associada, ordem). Campo {@code null} = mantem o atual. A origem da escrita
 * (USER|CLAUDE) e resolvida via {@link OriginProvider} - nunca recebida do chamador.
 * {@link NoSuchElementException} se o id nao existe (-&gt; 404).
 */
@Component
public class UpdateTrainingUseCase {

	private final TrainingRepository trainingRepository;
	private final OriginProvider originProvider;

	public UpdateTrainingUseCase(TrainingRepository trainingRepository, OriginProvider originProvider) {
		this.trainingRepository = trainingRepository;
		this.originProvider = originProvider;
	}

	public Training execute(UpdateTrainingCommand command) {
		Training current = trainingRepository.findById(command.id())
			.orElseThrow(() -> new NoSuchElementException("Training nao encontrado: " + command.id()));
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		Training updated = current.withUpdated(command.name(), command.description(), command.targetDurationMinutes(),
				command.targetRepetitions(), command.goalId(), command.orderIndex(), origin, now);
		return trainingRepository.save(updated);
	}
}
