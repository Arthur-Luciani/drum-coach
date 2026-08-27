package dev.drumcoach.application;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: atualizacao parcial de um exercicio - {@code pattern} e/ou
 * {@code howToExecute} (usado pelo editor da grade). A origem da escrita (USER|CLAUDE) e
 * resolvida via {@link OriginProvider}.
 *
 * {@code kind} e imutavel (ver ADR-0011): se o comando trouxer um {@code kind} diferente do
 * atual, lanca {@link IllegalArgumentException} (-&gt; 400). O {@code pattern}, quando
 * enviado, e re-validado por {@link DrumPattern} contra o {@code kind} atual.
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
		Exercise updated = current.withPatternAndNotes(canonicalPattern, command.howToExecute(), origin, now);
		return exerciseRepository.save(updated);
	}
}
