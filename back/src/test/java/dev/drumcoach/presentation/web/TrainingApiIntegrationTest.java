package dev.drumcoach.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Testes ponta a ponta da Fase 5: {@code PATCH /api/trainings/{id}} (edicao parcial) e
 * {@code DELETE /api/trainings/{id}} (cascata para exercicios/trechos, 409 quando ha
 * execucoes registradas, 404 quando o id nao existe).
 */
@SpringBootTest(classes = WebApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class TrainingApiIntegrationTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void overrideDbPath(DynamicPropertyRegistry registry) {
		registry.add("drumcoach.data.db-path", () -> tempDir.resolve("drum-coach-training-test.db").toString());
	}

	@LocalServerPort
	private int port;

	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
	};

	private RestClient client() {
		return RestClient.create("http://localhost:" + port);
	}

	private static HttpClientErrorException expectHttpError(Runnable call) {
		try {
			call.run();
		}
		catch (HttpClientErrorException e) {
			return e;
		}
		return fail("esperava um erro HTTP 4xx, mas a chamada teve sucesso");
	}

	private long createTraining(RestClient client) {
		Map<String, Object> body = client.post()
			.uri("/api/trainings")
			.body(Map.of("name", "Treino Fase 5", "description", "original", "targetDurationMinutes", 30,
					"orderIndex", 0))
			.retrieve()
			.body(MAP);
		return ((Number) body.get("id")).longValue();
	}

	@Test
	void patchUpdatesASingleFieldLeavingTheRestIntact() {
		RestClient client = client();
		long trainingId = createTraining(client);

		Map<String, Object> updated = client.patch()
			.uri("/api/trainings/{id}", trainingId)
			.body(Map.of("targetRepetitions", 5))
			.retrieve()
			.body(MAP);

		assertThat(updated).isNotNull();
		assertThat(updated.get("targetRepetitions")).isEqualTo(5);
		assertThat(updated.get("name")).isEqualTo("Treino Fase 5");
		assertThat(updated.get("description")).isEqualTo("original");
		assertThat(updated.get("targetDurationMinutes")).isEqualTo(30);
		assertThat(updated.get("lastModifiedBy")).isEqualTo("USER");
	}

	@Test
	void deletesEmptyTrainingReturns204() {
		RestClient client = client();
		long trainingId = createTraining(client);

		// exercicio filho: deve sumir junto (cascata)
		client.post()
			.uri("/api/trainings/{id}/exercises", trainingId)
			.body(Map.of("name", "Aquecimento", "exerciseType", "livre", "kind", "TRANSCRICAO", "orderIndex", 0))
			.retrieve()
			.toBodilessEntity();

		var deleteResponse = client.delete().uri("/api/trainings/{id}", trainingId).retrieve().toBodilessEntity();
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		List<Map<String, Object>> all = client.get()
			.uri("/api/trainings")
			.retrieve()
			.body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
			});
		assertThat(all).noneMatch(t -> ((Number) t.get("id")).longValue() == trainingId);
	}

	@Test
	void deletingTrainingThatHasExecutionsReturns409() {
		RestClient client = client();
		long trainingId = createTraining(client);

		client.post()
			.uri("/api/executions")
			.body(Map.of("trainingId", trainingId, "executionDate", "2026-08-27"))
			.retrieve()
			.toBodilessEntity();

		HttpClientErrorException error = expectHttpError(
				() -> client.delete().uri("/api/trainings/{id}", trainingId).retrieve().toBodilessEntity());
		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(error.getResponseBodyAsString()).contains("execucao");

		// o treino continua la
		List<Map<String, Object>> all = client.get()
			.uri("/api/trainings")
			.retrieve()
			.body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
			});
		assertThat(all).anyMatch(t -> ((Number) t.get("id")).longValue() == trainingId);
	}

	@Test
	void patchAndDeleteReturn404ForUnknownTraining() {
		RestClient client = client();

		HttpClientErrorException patchError = expectHttpError(
				() -> client.patch()
					.uri("/api/trainings/{id}", 987654)
					.body(Map.of("name", "x"))
					.retrieve()
					.toBodilessEntity());
		assertThat(patchError.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		HttpClientErrorException deleteError = expectHttpError(
				() -> client.delete().uri("/api/trainings/{id}", 987654).retrieve().toBodilessEntity());
		assertThat(deleteError.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
