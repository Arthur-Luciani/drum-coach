package dev.drumcoach.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Training;

/** Caso de uso: listar treinos, opcionalmente filtrados por meta ({@code goalId}). */
@Component
public class ListTrainingsUseCase {

	private final TrainingRepository trainingRepository;

	public ListTrainingsUseCase(TrainingRepository trainingRepository) {
		this.trainingRepository = trainingRepository;
	}

	public List<Training> execute(Long goalId) {
		return goalId != null ? trainingRepository.findByGoalId(goalId) : trainingRepository.findAll();
	}
}
