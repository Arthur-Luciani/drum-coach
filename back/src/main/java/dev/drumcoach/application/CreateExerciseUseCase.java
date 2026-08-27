package dev.drumcoach.application;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: criar um exercicio dentro de um treino. A origem da escrita (USER|CLAUDE)
 * e resolvida via {@link OriginProvider} - nunca recebida do chamador (controller REST ou
 * tool MCP).
 *
 * Quando {@code kind == TOCA_JUNTO} e ha {@code pattern}, ele e validado estruturalmente
 * por {@link DrumPattern} e persistido na forma canonica. Um {@code pattern} presente com
 * {@code kind == TRANSCRICAO} e rejeitado (400).
 */
@Component
public class CreateExerciseUseCase {

	private final ExerciseRepository exerciseRepository;
	private final OriginProvider originProvider;

	public CreateExerciseUseCase(ExerciseRepository exerciseRepository, OriginProvider originProvider) {
		this.exerciseRepository = exerciseRepository;
		this.originProvider = originProvider;
	}

	public Exercise execute(CreateExerciseCommand command) {
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		ExerciseKind kind = Objects.requireNonNull(command.kind(), "kind must not be null");
		String canonicalPattern = DrumPattern.canonicalOrNull(kind, command.pattern());
		Exercise exercise = Exercise.createNew(command.trainingId(), command.name(), command.exerciseType(), kind,
				command.howToExecute(), canonicalPattern, command.targetBpm(), command.targetDurationSeconds(),
				command.videoSourceType(), command.videoUrl(), command.videoFilePath(), command.orderIndex(), origin,
				now);
		return exerciseRepository.save(exercise);
	}
}
