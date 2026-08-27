package dev.drumcoach.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Training;

/**
 * Caso de uso: buscar uma meta com os treinos que pertencem direto a ela (ver ADR-0009 -
 * Treino referencia Goal diretamente, sem um Plano intermediario) e o progresso de cada
 * um (quantas {@code Execution} existem para aquele treino vs. {@code targetRepetitions}).
 */
@Component
public class GetGoalDetailUseCase {

	private final GoalRepository goalRepository;
	private final TrainingRepository trainingRepository;
	private final ExecutionRepository executionRepository;

	public GetGoalDetailUseCase(GoalRepository goalRepository, TrainingRepository trainingRepository,
			ExecutionRepository executionRepository) {
		this.goalRepository = goalRepository;
		this.trainingRepository = trainingRepository;
		this.executionRepository = executionRepository;
	}

	public Optional<GoalDetail> execute(long goalId) {
		return goalRepository.findById(goalId).map(goal -> {
			List<Training> trainings = trainingRepository.findByGoalId(goalId);
			List<TrainingWithProgress> withProgress = trainings.stream()
				.map(training -> new TrainingWithProgress(training,
						executionRepository.countByTrainingId(training.id())))
				.toList();
			return new GoalDetail(goal, withProgress);
		});
	}
}
