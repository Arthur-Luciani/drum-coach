package dev.drumcoach.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Exercise;

/** Caso de uso: listar os exercicios de um treino. */
@Component
public class ListExercisesByTrainingUseCase {

	private final ExerciseRepository exerciseRepository;

	public ListExercisesByTrainingUseCase(ExerciseRepository exerciseRepository) {
		this.exerciseRepository = exerciseRepository;
	}

	public List<Exercise> execute(long trainingId) {
		return exerciseRepository.findByTrainingId(trainingId);
	}
}
