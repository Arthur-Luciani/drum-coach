package dev.drumcoach.application;

import java.time.Instant;

import org.springframework.stereotype.Component;

import dev.drumcoach.domain.Execution;
import dev.drumcoach.domain.ExecutionExerciseLog;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: registrar uma execucao real de um treino, com seus logs de exercicio
 * (agregado persistido numa unica operacao). A origem da escrita (USER|CLAUDE) e
 * resolvida via {@link OriginProvider} - nunca recebida do chamador (controller REST ou
 * tool MCP). Os logs nao tem auditoria propria: a origem/timestamp sao os da execucao pai
 * (ver ADR-0005).
 */
@Component
public class CreateExecutionUseCase {

	private final ExecutionRepository executionRepository;
	private final OriginProvider originProvider;

	public CreateExecutionUseCase(ExecutionRepository executionRepository, OriginProvider originProvider) {
		this.executionRepository = executionRepository;
		this.originProvider = originProvider;
	}

	public Execution execute(CreateExecutionCommand command) {
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		var logs = command.logs()
			.stream()
			.map(log -> ExecutionExerciseLog.createNew(log.exerciseId(), log.achievedBpm(),
					log.actualDurationSeconds(), log.notes(), now))
			.toList();
		Execution execution = Execution.createNew(command.trainingId(), command.executionDate(),
				command.actualDurationMinutes(), command.feeling(), command.generalNotes(), command.goalId(), logs,
				origin, now);
		return executionRepository.save(execution);
	}
}
