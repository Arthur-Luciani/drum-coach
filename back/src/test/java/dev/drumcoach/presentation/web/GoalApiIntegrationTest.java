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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

/**
 * Teste ponta a ponta da Fase 0 (spike): sobe o contexto completo de
 * {@link WebApplication} (mesmos scanBasePackages do processo real) contra um arquivo
 * SQLite temporario e exercita {@code POST /api/goals} + {@code GET /api/goals} via
 * HTTP de verdade - valida na pratica os riscos tecnicos #2/#3/#4/#5 (dialect,
 * getGeneratedKeys, conversoes de timestamp/enum, PRAGMAs) descritos no plano de
 * implementacao.
 *
 * Usa {@link RestClient} (spring-web) em vez de {@code TestRestTemplate}, que no Spring
 * Boot 4 foi movido para o modulo separado {@code spring-boot-resttestclient} - evitar
 * mais uma dependencia so para este spike.
 */
@SpringBootTest(classes = WebApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
class GoalApiIntegrationTest {

	@TempDir
	static Path tempDir;

	@DynamicPropertySource
	static void overrideDbPath(DynamicPropertyRegistry registry) {
		registry.add("drumcoach.data.db-path", () -> tempDir.resolve("drum-coach-test.db").toString());
	}

	@LocalServerPort
	private int port;

	@Test
	void createsAndListsGoals() {
		RestClient client = RestClient.create("http://localhost:" + port);

		Map<String, Object> health = client.get()
			.uri("/api/health")
			.retrieve()
			.body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
			});
		assertThat(health).containsEntry("status", "UP");

		Map<String, Object> request = Map.of("title", "Tocar um rufo de 30 segundos sem errar");
		var createdResponse = client.post()
			.uri("/api/goals")
			.body(request)
			.retrieve()
			.toEntity(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
			});

		assertThat(createdResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> created = createdResponse.getBody();
		assertThat(created).isNotNull();
		assertThat(created.get("id")).isNotNull();
		assertThat(created.get("title")).isEqualTo("Tocar um rufo de 30 segundos sem errar");
		assertThat(created.get("status")).isEqualTo("NOT_STARTED");
		assertThat(created.get("createdBy")).isEqualTo("USER");
		assertThat(created.get("lastModifiedBy")).isEqualTo("USER");

		List<Map<String, Object>> goals = client.get()
			.uri("/api/goals")
			.retrieve()
			.body(new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {
			});

		assertThat(goals).isNotNull().hasSize(1);
		assertThat(goals.get(0).get("title")).isEqualTo("Tocar um rufo de 30 segundos sem errar");
	}
}
