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
 * Teste ponta a ponta: uma execucao avulsa (treino sem meta associada) com multiplos
 * {@code execution_exercise_log} persistidos numa unica chamada, e a auditoria de origem
 * (header {@code X-Drum-Coach-Actor: CLAUDE}) propagada da execucao pai para os logs
 * filhos (que nao tem auditoria propria - ver ADR-0005).
 */
@SpringBootTest(classes = WebApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class ExecutionApiIntegrationTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void overrideDbPath(DynamicPropertyRegistry registry) {
		registry.add("drumcoach.data.db-path", () -> tempDir.resolve("drum-coach-execution-test.db").toString());
	}

	@LocalServerPort
	private int port;

	@Test
	void createsExecutionWithMultipleExerciseLogsAndTracksClaudeOrigin() {
		RestClient client = RestClient.create("http://localhost:" + port);

		// treino avulso (sem goalId)
		Map<String, Object> trainingRequest = Map.of("name", "Treino livre", "targetDurationMinutes", 45,
				"orderIndex", 0);
		Map<String, Object> training = client.post()
			.uri("/api/trainings")
			.body(trainingRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.getBody();
		assertThat(training).isNotNull();
		assertThat(training.get("goalId")).isNull();
		Number trainingId = (Number) training.get("id");

		Map<String, Object> exercise1 = createExercise(client, trainingId, "Single stroke roll", 0);
		Map<String, Object> exercise2 = createExercise(client, trainingId, "Double stroke roll", 1);

		Map<String, Object> executionRequest = Map.of("trainingId", trainingId.longValue(), "executionDate",
				"2026-08-26", "actualDurationMinutes", 40, "feeling", "cansado mas produtivo", "generalNotes",
				"foco em consistencia", "logs",
				List.of(Map.of("exerciseId", ((Number) exercise1.get("id")).longValue(), "achievedBpm", 120, "notes",
						"estavel"),
						Map.of("exerciseId", ((Number) exercise2.get("id")).longValue(), "achievedBpm", 100, "notes",
								"melhorar mao fraca")));

		var response = client.post()
			.uri("/api/executions")
			.header("X-Drum-Coach-Actor", "CLAUDE")
			.body(executionRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> execution = response.getBody();
		assertThat(execution).isNotNull();
		assertThat(execution.get("createdBy")).isEqualTo("CLAUDE");
		assertThat(execution.get("lastModifiedBy")).isEqualTo("CLAUDE");

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> logs = (List<Map<String, Object>>) execution.get("logs");
		assertThat(logs).hasSize(2);
		assertThat(logs).extracting(log -> log.get("achievedBpm")).containsExactlyInAnyOrder(120, 100);
	}

	/** Sessao livre (Fase 3e-3): {@code trainingId} nulo, sem logs de exercicio. */
	@Test
	void createsExecutionWithoutTraining_freeSession() {
		RestClient client = RestClient.create("http://localhost:" + port);

		Map<String, Object> executionRequest = new java.util.HashMap<>();
		executionRequest.put("trainingId", null);
		executionRequest.put("executionDate", "2026-08-27");
		executionRequest.put("actualDurationMinutes", 12);
		executionRequest.put("feeling", "só cronometrando");
		executionRequest.put("generalNotes", "testando um groove novo de shuffle");
		executionRequest.put("goalId", null);
		executionRequest.put("logs", List.of());

		var response = client.post()
			.uri("/api/executions")
			.body(executionRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> execution = response.getBody();
		assertThat(execution).isNotNull();
		assertThat(execution.get("trainingId")).isNull();
		assertThat(execution.get("createdBy")).isEqualTo("USER");

		var listResponse = client.get()
			.uri("/api/executions")
			.retrieve()
			.toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {
			});
		assertThat(listResponse.getBody()).anySatisfy(e -> assertThat(e.get("id")).isEqualTo(execution.get("id")));
	}

	private Map<String, Object> createExercise(RestClient client, Number trainingId, String name, int orderIndex) {
		Map<String, Object> exerciseRequest = Map.of("name", name, "exerciseType", "rudimento", "kind", "TRANSCRICAO",
				"howToExecute", "metronomo lento, subir gradualmente", "orderIndex", orderIndex);
		return client.post()
			.uri("/api/trainings/{trainingId}/exercises", trainingId)
			.body(exerciseRequest)
			.retrieve()
			.toEntity(new ParameterizedTypeReference<Map<String, Object>>() {
			})
			.getBody();
	}
}
