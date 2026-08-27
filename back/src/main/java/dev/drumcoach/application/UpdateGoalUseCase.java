package dev.drumcoach.application;

import java.time.Instant;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.drumcoach.domain.Goal;
import dev.drumcoach.domain.Origin;

/**
 * Caso de uso: atualizar parcialmente o progresso de uma meta (status/descricao) e/ou
 * marca-la como "em foco" (Dashboard - ver ADR-0008/ADR-0009). A origem da escrita
 * (USER|CLAUDE) e resolvida via {@link OriginProvider} - nunca recebida do chamador
 * (controller REST ou tool MCP).
 *
 * Quando {@code command.inFocus()} e {@code true}, a operacao garante no maximo uma meta
 * em foco por vez: desfoca todas as demais metas atualmente em foco e so entao foca esta,
 * tudo na mesma transacao ({@code @Transactional}).
 */
@Component
public class UpdateGoalUseCase {

	private final GoalRepository goalRepository;
	private final OriginProvider originProvider;

	public UpdateGoalUseCase(GoalRepository goalRepository, OriginProvider originProvider) {
		this.goalRepository = goalRepository;
		this.originProvider = originProvider;
	}

	@Transactional
	public Goal execute(UpdateGoalCommand command) {
		Goal current = goalRepository.findById(command.id())
			.orElseThrow(() -> new NoSuchElementException("Goal nao encontrada: " + command.id()));
		Origin origin = originProvider.currentOrigin();
		Instant now = Instant.now();
		Goal updated = current.withUpdate(command.status(), command.description(), origin, now);

		if (Boolean.TRUE.equals(command.inFocus())) {
			for (Goal other : goalRepository.findAll()) {
				if (other.inFocus() && !other.id().equals(updated.id())) {
					goalRepository.save(other.withFocus(false, origin, now));
				}
			}
			updated = updated.withFocus(true, origin, now);
		}

		return goalRepository.save(updated);
	}
}
