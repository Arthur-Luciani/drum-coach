package dev.drumcoach.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Testes ponta a ponta cobrindo a modelagem corrigida pelo ADR-0009 (Treino pertence
 * direto a Meta, sem Plano intermediario) e o campo {@code goal.inFocus} do ADR-0008:
 *
 * <ul>
 * <li>{@code GET /api/goals/{id}} traz os treinos vinculados direto a meta ({@code
 * goalId}), cada um com o progresso (execucoes registradas vs. {@code
 * targetRepetitions}) - substitui o antigo {@code GET /api/plans/{id}}.</li>
 * <li>{@code PATCH /api/goals/{id}} com {@code inFocus: true} desfoca atomicamente
 * qualquer outra meta em foco antes de focar a meta alvo.</li>
 * </ul>
 */
@SpringBootTest(classes = WebApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class GoalDetailApiIntegrationTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void overrideDbPath(DynamicPropertyRegistry registry) {
		registry.add("drumcoach.data.db-path", () -> tempDir.resolve("drum-coach-goal-detail-test.db").toString());
	}

	@LocalServerPort
	private int port;

	@Test
	void goalDetailReflectsTrainingsWithProgress() {
		RestClient client = RestClient.create("http://localhost:" + port);

		// 1. cria a meta
		Map<String, Object> goalRequest = Map.of("title", "Evoluir singles e doubles");
		Map<String, Object> goal = client.post()
			.uri("/api/goals")
			.body(goalRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.getBody();
		assertThat(goal).isNotNull();
		Number goalId = (Number) goal.get("id");

		// 2. cria 2 treinos vinculados direto a meta (goalId), sem Plano intermediario
		Number trainingAId = createTraining(client, goalId, "Treino A - 1h30", 90, 3);
		Number trainingBId = createTraining(client, goalId, "Treino B - 45min", 45, null);

		// 3. adiciona um exercicio ao treino A e registra 2 execucoes dele
		Map<String, Object> exerciseRequest = Map.of("name", "Paradiddle", "exerciseType", "rudimento", "kind",
				"TRANSCRICAO", "howToExecute", "RLRR LRLL, metronomo a 80 bpm", "orderIndex", 0);
		Map<String, Object> exercise = client.post()
			.uri("/api/trainings/{trainingId}/exercises", trainingAId)
			.body(exerciseRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.getBody();
		assertThat(exercise).isNotNull();
		Number exerciseId = (Number) exercise.get("id");

		for (int i = 0; i < 2; i++) {
			Map<String, Object> executionRequest = Map.of("trainingId", trainingAId.longValue(), "executionDate",
					"2026-08-2" + i, "actualDurationMinutes", 85, "feeling", "bom", "logs",
					List.of(Map.of("exerciseId", exerciseId.longValue(), "achievedBpm", 90 + i, "notes", "ok")));
			var executionResponse = client.post()
				.uri("/api/executions")
				.body(executionRequest)
				.retrieve()
				.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
				});
			assertThat(executionResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		}

		// 4. GET /api/goals/{id} deve trazer os 2 treinos, com o progresso correto de cada
		Map<String, Object> goalDetail = client.get()
			.uri("/api/goals/{id}", goalId)
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(goalDetail).isNotNull();
		@SuppressWarnings("unchecked")
		Map<String, Object> goalInDetail = (Map<String, Object>) goalDetail.get("goal");
		assertThat(goalInDetail.get("id")).isEqualTo(goalId.intValue());

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> trainings = (List<Map<String, Object>>) goalDetail.get("trainings");
		assertThat(trainings).hasSize(2);

		Map<String, Object> trainingAProgress = trainings.stream()
			.filter(t -> t.get("trainingId").equals(trainingAId.intValue()))
			.findFirst()
			.orElseThrow();
		assertThat(trainingAProgress.get("targetRepetitions")).isEqualTo(3);
		assertThat(((Number) trainingAProgress.get("completedCount")).longValue()).isEqualTo(2L);

		Map<String, Object> trainingBProgress = trainings.stream()
			.filter(t -> t.get("trainingId").equals(trainingBId.intValue()))
			.findFirst()
			.orElseThrow();
		assertThat(trainingBProgress.get("targetRepetitions")).isNull();
		assertThat(((Number) trainingBProgress.get("completedCount")).longValue()).isEqualTo(0L);
	}

	@Test
	void focusingAGoalUnfocusesThePreviouslyFocusedOne() {
		RestClient client = RestClient.create("http://localhost:" + port);

		Number firstGoalId = createGoal(client, "Meta 1");
		Number secondGoalId = createGoal(client, "Meta 2");

		// foca a primeira meta
		Map<String, Object> firstFocused = client.patch()
			.uri("/api/goals/{id}", firstGoalId)
			.body(Map.of("inFocus", true))
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(firstFocused).isNotNull();
		assertThat(firstFocused.get("inFocus")).isEqualTo(true);

		// foca a segunda - deve desfocar a primeira atomicamente
		Map<String, Object> secondFocused = client.patch()
			.uri("/api/goals/{id}", secondGoalId)
			.body(Map.of("inFocus", true))
			.retrieve()
			.body(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(secondFocused).isNotNull();
		assertThat(secondFocused.get("inFocus")).isEqualTo(true);

		// confirma no banco (via GET /api/goals) que so a segunda meta esta em foco
		List<Map<String, Object>> goals = client.get()
			.uri("/api/goals")
			.retrieve()
			.body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
			});
		assertThat(goals).isNotNull();

		Map<String, Object> firstGoalReloaded = goals.stream()
			.filter(g -> g.get("id").equals(firstGoalId.intValue()))
			.findFirst()
			.orElseThrow();
		Map<String, Object> secondGoalReloaded = goals.stream()
			.filter(g -> g.get("id").equals(secondGoalId.intValue()))
			.findFirst()
			.orElseThrow();

		assertThat(firstGoalReloaded.get("inFocus")).isEqualTo(false);
		assertThat(secondGoalReloaded.get("inFocus")).isEqualTo(true);
	}

	private Number createGoal(RestClient client, String title) {
		Map<String, Object> created = client.post()
			.uri("/api/goals")
			.body(Map.of("title", title))
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.getBody();
		assertThat(created).isNotNull();
		assertThat(created.get("inFocus")).isEqualTo(false);
		return (Number) created.get("id");
	}

	private Number createTraining(RestClient client, Number goalId, String name, int targetDurationMinutes,
			Integer targetRepetitions) {
		Map<String, Object> request = new java.util.HashMap<>();
		request.put("goalId", goalId.longValue());
		request.put("name", name);
		request.put("targetDurationMinutes", targetDurationMinutes);
		request.put("targetRepetitions", targetRepetitions);
		request.put("orderIndex", 0);
		Map<String, Object> created = client.post()
			.uri("/api/trainings")
			.body(request)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.getBody();
		assertThat(created).isNotNull();
		assertThat(created.get("goalId")).isEqualTo(goalId.intValue());
		return (Number) created.get("id");
	}
}
