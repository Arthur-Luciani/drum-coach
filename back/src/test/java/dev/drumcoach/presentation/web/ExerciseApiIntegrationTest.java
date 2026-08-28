package dev.drumcoach.presentation.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.HashMap;
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
 * Testes ponta a ponta da Fase 4a (ADR-0011): eixo {@code kind} no exercicio, documento
 * {@code pattern} tocavel validado estruturalmente, e trechos marcados
 * ({@code exercise_passage}) como colecao filha incremental.
 */
@SpringBootTest(classes = WebApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class ExerciseApiIntegrationTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void overrideDbPath(DynamicPropertyRegistry registry) {
		registry.add("drumcoach.data.db-path", () -> tempDir.resolve("drum-coach-exercise-test.db").toString());
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
			.body(Map.of("name", "Treino 4a", "targetDurationMinutes", 30, "orderIndex", 0))
			.retrieve()
			.body(MAP);
		return ((Number) body.get("id")).longValue();
	}

	private static Map<String, Object> validPattern() {
		Map<String, Object> pattern = new HashMap<>();
		pattern.put("version", 1);
		pattern.put("timeSignature", List.of(4, 4));
		pattern.put("stepsPerBeat", 4);
		pattern.put("tuplet", false);
		pattern.put("bars", 1);
		pattern.put("voices", List.of("hihat", "snare", "kick"));
		pattern.put("hits", Map.of("hihat", List.of(0, 2, 4, 6, 8, 10, 12, 14), "snare", List.of(4, 12), "kick",
				List.of(0, 8)));
		return pattern;
	}

	@Test
	void createsTocaJuntoExerciseWithValidPattern() {
		RestClient client = client();
		long trainingId = createTraining(client);

		Map<String, Object> request = new HashMap<>();
		request.put("name", "Groove 4/4");
		request.put("exerciseType", "groove");
		request.put("kind", "TOCA_JUNTO");
		request.put("pattern", validPattern());
		request.put("targetBpm", 90);
		request.put("orderIndex", 0);

		var response = client.post()
			.uri("/api/trainings/{trainingId}/exercises", trainingId)
			.body(request)
			.retrieve()
			.toEntity(MAP);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> created = response.getBody();
		assertThat(created).isNotNull();
		assertThat(created.get("kind")).isEqualTo("TOCA_JUNTO");
		assertThat(created.get("createdBy")).isEqualTo("USER");
		@SuppressWarnings("unchecked")
		Map<String, Object> pattern = (Map<String, Object>) created.get("pattern");
		assertThat(pattern).isNotNull();
		assertThat(pattern.get("stepsPerBeat")).isEqualTo(4);
		assertThat(created.get("passages")).isEqualTo(List.of());
	}

	@Test
	void rejectsTocaJuntoExerciseWithInvalidPattern() {
		RestClient client = client();
		long trainingId = createTraining(client);

		Map<String, Object> badPattern = validPattern();
		badPattern.put("voices", List.of("cowbell", "snare"));

		Map<String, Object> request = new HashMap<>();
		request.put("name", "Groove ruim");
		request.put("exerciseType", "groove");
		request.put("kind", "TOCA_JUNTO");
		request.put("pattern", badPattern);
		request.put("orderIndex", 0);

		HttpClientErrorException error = expectHttpError(
				() -> client.post()
					.uri("/api/trainings/{trainingId}/exercises", trainingId)
					.body(request)
					.retrieve()
					.toBodilessEntity());
		assertThat(error).isNotNull();
		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(error.getResponseBodyAsString()).contains("vocabulario");
	}

	@Test
	void rejectsTranscricaoExerciseCarryingAPattern() {
		RestClient client = client();
		long trainingId = createTraining(client);

		Map<String, Object> request = new HashMap<>();
		request.put("name", "Transcrever intro");
		request.put("exerciseType", "leitura");
		request.put("kind", "TRANSCRICAO");
		request.put("pattern", validPattern());
		request.put("orderIndex", 0);

		HttpClientErrorException error = expectHttpError(
				() -> client.post()
					.uri("/api/trainings/{trainingId}/exercises", trainingId)
					.body(request)
					.retrieve()
					.toBodilessEntity());
		assertThat(error).isNotNull();
		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(error.getResponseBodyAsString()).contains("TOCA_JUNTO");
	}

	@Test
	void patchesPatternAndNotesOfAnExistingExercise() {
		RestClient client = client();
		long trainingId = createTraining(client);

		Map<String, Object> create = new HashMap<>();
		create.put("name", "Groove sem pattern ainda");
		create.put("exerciseType", "groove");
		create.put("kind", "TOCA_JUNTO");
		create.put("orderIndex", 0);
		Map<String, Object> created = client.post()
			.uri("/api/trainings/{trainingId}/exercises", trainingId)
			.body(create)
			.retrieve()
			.body(MAP);
		long exerciseId = ((Number) created.get("id")).longValue();
		assertThat(created.get("pattern")).isNull();

		Map<String, Object> patch = new HashMap<>();
		patch.put("pattern", validPattern());
		patch.put("howToExecute", "chimbal fechado, foco no tempo 2 e 4");

		Map<String, Object> updated = client.patch()
			.uri("/api/exercises/{id}", exerciseId)
			.body(patch)
			.retrieve()
			.body(MAP);
		assertThat(updated).isNotNull();
		assertThat(updated.get("howToExecute")).isEqualTo("chimbal fechado, foco no tempo 2 e 4");
		@SuppressWarnings("unchecked")
		Map<String, Object> pattern = (Map<String, Object>) updated.get("pattern");
		assertThat(pattern).isNotNull();
		assertThat(pattern.get("bars")).isEqualTo(1);
	}

	@Test
	void rejectsPatchThatTriesToChangeKind() {
		RestClient client = client();
		long trainingId = createTraining(client);

		Map<String, Object> create = new HashMap<>();
		create.put("name", "Transcricao fixa");
		create.put("exerciseType", "leitura");
		create.put("kind", "TRANSCRICAO");
		create.put("orderIndex", 0);
		Map<String, Object> created = client.post()
			.uri("/api/trainings/{trainingId}/exercises", trainingId)
			.body(create)
			.retrieve()
			.body(MAP);
		long exerciseId = ((Number) created.get("id")).longValue();

		HttpClientErrorException error = expectHttpError(
				() -> client.patch()
					.uri("/api/exercises/{id}", exerciseId)
					.body(Map.of("kind", "TOCA_JUNTO", "howToExecute", "x"))
					.retrieve()
					.toBodilessEntity());
		assertThat(error).isNotNull();
		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(error.getResponseBodyAsString()).contains("imutavel");

		// PATCH mandando o mesmo kind e aceito
		Map<String, Object> ok = client.patch()
			.uri("/api/exercises/{id}", exerciseId)
			.body(Map.of("kind", "TRANSCRICAO", "howToExecute", "anotar a levada do prato"))
			.retrieve()
			.body(MAP);
		assertThat(ok.get("howToExecute")).isEqualTo("anotar a levada do prato");
	}

	@Test
	void addsListsAndDeletesMarkedPassages() {
		RestClient client = client();
		long trainingId = createTraining(client);

		Map<String, Object> create = new HashMap<>();
		create.put("name", "Transcrever solo");
		create.put("exerciseType", "leitura");
		create.put("kind", "TRANSCRICAO");
		create.put("orderIndex", 0);
		Map<String, Object> created = client.post()
			.uri("/api/trainings/{trainingId}/exercises", trainingId)
			.body(create)
			.retrieve()
			.body(MAP);
		long exerciseId = ((Number) created.get("id")).longValue();

		var firstResponse = client.post()
			.uri("/api/exercises/{id}/passages", exerciseId)
			.body(Map.of("fromSeconds", 42, "toSeconds", 55, "label", "virada do refrao"))
			.retrieve()
			.toEntity(MAP);
		assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		long firstPassageId = ((Number) firstResponse.getBody().get("id")).longValue();

		// segundo trecho, com fromSeconds menor - deve aparecer primeiro na listagem ordenada
		Map<String, Object> second = new HashMap<>();
		second.put("fromSeconds", 10);
		client.post().uri("/api/exercises/{id}/passages", exerciseId).body(second).retrieve().toBodilessEntity();

		Map<String, Object> detail = client.get().uri("/api/exercises/{id}", exerciseId).retrieve().body(MAP);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> passages = (List<Map<String, Object>>) detail.get("passages");
		assertThat(passages).hasSize(2);
		assertThat(passages.get(0).get("fromSeconds")).isEqualTo(10);
		assertThat(passages.get(0).get("toSeconds")).isNull();
		assertThat(passages.get(1).get("fromSeconds")).isEqualTo(42);
		assertThat(passages.get(1).get("label")).isEqualTo("virada do refrao");

		var deleteResponse = client.delete()
			.uri("/api/exercises/{id}/passages/{passageId}", exerciseId, firstPassageId)
			.retrieve()
			.toBodilessEntity();
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		Map<String, Object> afterDelete = client.get().uri("/api/exercises/{id}", exerciseId).retrieve().body(MAP);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> remaining = (List<Map<String, Object>>) afterDelete.get("passages");
		assertThat(remaining).hasSize(1);
		assertThat(remaining.get(0).get("fromSeconds")).isEqualTo(10);
	}

	@Test
	void getReturns404ForUnknownExercise() {
		RestClient client = client();
		HttpClientErrorException error = expectHttpError(
				() -> client.get().uri("/api/exercises/{id}", 999999).retrieve().toBodilessEntity());
		assertThat(error).isNotNull();
		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	// ===================== Fase 5: PATCH ampliado + DELETE =====================

	private long createSimpleExercise(RestClient client, long trainingId) {
		Map<String, Object> create = new HashMap<>();
		create.put("name", "Rulo");
		create.put("exerciseType", "rudimento");
		create.put("kind", "TRANSCRICAO");
		create.put("orderIndex", 0);
		Map<String, Object> created = client.post()
			.uri("/api/trainings/{trainingId}/exercises", trainingId)
			.body(create)
			.retrieve()
			.body(MAP);
		return ((Number) created.get("id")).longValue();
	}

	@Test
	void patchUpdatesNameAndTargetBpmLeavingOtherFieldsIntact() {
		RestClient client = client();
		long trainingId = createTraining(client);
		long exerciseId = createSimpleExercise(client, trainingId);

		Map<String, Object> patch = new HashMap<>();
		patch.put("name", "Rulo de 5");
		patch.put("targetBpm", 120);

		Map<String, Object> updated = client.patch()
			.uri("/api/exercises/{id}", exerciseId)
			.body(patch)
			.retrieve()
			.body(MAP);
		assertThat(updated).isNotNull();
		assertThat(updated.get("name")).isEqualTo("Rulo de 5");
		assertThat(updated.get("targetBpm")).isEqualTo(120);
		assertThat(updated.get("exerciseType")).isEqualTo("rudimento");
		assertThat(updated.get("kind")).isEqualTo("TRANSCRICAO");
	}

	@Test
	void deletesExerciseWithoutLogsReturns204ThenGetIs404() {
		RestClient client = client();
		long trainingId = createTraining(client);
		long exerciseId = createSimpleExercise(client, trainingId);

		var deleteResponse = client.delete()
			.uri("/api/exercises/{id}", exerciseId)
			.retrieve()
			.toBodilessEntity();
		assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		HttpClientErrorException error = expectHttpError(
				() -> client.get().uri("/api/exercises/{id}", exerciseId).retrieve().toBodilessEntity());
		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void deletingExerciseThatHasAnExecutionLogReturns409() {
		RestClient client = client();
		long trainingId = createTraining(client);
		long exerciseId = createSimpleExercise(client, trainingId);

		client.post()
			.uri("/api/executions")
			.body(Map.of("trainingId", trainingId, "executionDate", "2026-08-27", "logs",
					List.of(Map.of("exerciseId", exerciseId, "achievedBpm", 100))))
			.retrieve()
			.toBodilessEntity();

		HttpClientErrorException error = expectHttpError(
				() -> client.delete().uri("/api/exercises/{id}", exerciseId).retrieve().toBodilessEntity());
		assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(error.getResponseBodyAsString()).contains("execucao");
	}

	@Test
	void deleteAndPatchReturn404ForUnknownExercise() {
		RestClient client = client();

		HttpClientErrorException deleteError = expectHttpError(
				() -> client.delete().uri("/api/exercises/{id}", 424242).retrieve().toBodilessEntity());
		assertThat(deleteError.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

		HttpClientErrorException patchError = expectHttpError(
				() -> client.patch()
					.uri("/api/exercises/{id}", 424242)
					.body(Map.of("name", "x"))
					.retrieve()
					.toBodilessEntity());
		assertThat(patchError.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}
}
