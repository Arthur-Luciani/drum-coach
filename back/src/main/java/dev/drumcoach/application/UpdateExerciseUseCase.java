package dev.drumcoach.application;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: atualizacao parcial de um exercicio - qualquer campo editavel
 * ({@code name}, {@code exerciseType}, {@code howToExecute}, {@code pattern},
 * {@code targetBpm}, {@code targetDurationSeconds}, campos de video, {@code orderIndex}).
 * Campo {@code null} = mantem o atual. A origem da escrita (USER|CLAUDE) e resolvida via
 * {@link OriginProvider}.
 *
 * {@code kind} e imutavel (ver ADR-0011): se o comando trouxer um {@code kind} diferente do
 * atual, lanca {@link IllegalArgumentException} (-&gt; 400). O {@code pattern}, quando
 * enviado, e re-validado por {@link DrumPattern} contra o {@code kind} atual.
 * {@link java.util.NoSuchElementException} se o id nao existe (-&gt; 404).
 */
@Component
public class UpdateExerciseUseCase {

	private final ExerciseRepository exerciseRepository;
	private final OriginProvider originProvider;

	public UpdateExerciseUseCase(ExerciseRepository exerciseRepository, OriginProvider originProvider) {
		this.exerciseRepository = exerciseRepository;
		this.originProvider = originProvider;
	}

	public Exercise execute(UpdateExerciseCommand command) {
		Exercise current = exerciseRepository.findById(command.id())
			.orElseThrow(() -> new NoSuchElementException("Exercise nao encontrado: " + command.id()));

		if (command.kind() != null && command.kind() != current.kind()) {
			throw new IllegalArgumentException("kind do exercicio e imutavel (atual: " + current.kind()
					+ ", recebido: " + command.kind() + ")");
		}

		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		String canonicalPattern = command.pattern() == null ? null
				: DrumPattern.canonicalOrNull(current.kind(), command.pattern());
		Exercise updated = current.withUpdated(command.name(), command.exerciseType(), command.howToExecute(),
				canonicalPattern, command.targetBpm(), command.targetDurationSeconds(), command.videoSourceType(),
				command.videoUrl(), command.videoFilePath(), command.orderIndex(), origin, now);
		return exerciseRepository.save(updated);
	}
}
