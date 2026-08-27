package dev.drumcoach.application;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Exercise;
import dev.drumcoach.domain.ExercisePassage;

/** Caso de uso: ler um exercicio por id, junto dos seus trechos marcados ({@code passages}). */
@Component
public class GetExerciseUseCase {

	private final ExerciseRepository exerciseRepository;

	public GetExerciseUseCase(ExerciseRepository exerciseRepository) {
		this.exerciseRepository = exerciseRepository;
	}

	public Result execute(long id) {
		Exercise exercise = exerciseRepository.findById(id)
			.orElseThrow(() -> new NoSuchElementException("Exercise nao encontrado: " + id));
		List<ExercisePassage> passages = exerciseRepository.findPassagesByExerciseId(id);
		return new Result(exercise, passages);
	}

	/** Exercicio + seus trechos marcados. */
	public record Result(Exercise exercise, List<ExercisePassage> passages) {
	}
}
