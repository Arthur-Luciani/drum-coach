package dev.drumcoach.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Goal;

/** Caso de uso: listar todas as metas cadastradas. */
@Component
public class ListGoalsUseCase {

	private final GoalRepository goalRepository;

	public ListGoalsUseCase(GoalRepository goalRepository) {
		this.goalRepository = goalRepository;
	}

	public List<Goal> execute() {
		return goalRepository.findAll();
	}
}
