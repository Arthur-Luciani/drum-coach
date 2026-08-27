package dev.drumcoach.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Execution;

/**
 * Caso de uso: listar execucoes, opcionalmente filtradas por treino ({@code trainingId})
 * e/ou por meta ({@code goalId}, filtro direto sobre {@code execution.goal_id} - ver
 * ADR-0009, nao existe mais {@code training.plan_id} para fazer join). Quando ambos sao
 * informados, {@code trainingId} tem prioridade (mais especifico).
 */
@Component
public class ListExecutionsUseCase {

	private final ExecutionRepository executionRepository;

	public ListExecutionsUseCase(ExecutionRepository executionRepository) {
		this.executionRepository = executionRepository;
	}

	public List<Execution> execute(Long trainingId, Long goalId) {
		if (trainingId != null) {
			return executionRepository.findByTrainingId(trainingId);
		}
		if (goalId != null) {
			return executionRepository.findByGoalId(goalId);
		}
		return executionRepository.findAll();
	}
}
