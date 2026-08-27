package dev.drumcoach.presentation.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/**
 * Entry point HTTP (o "caderno" acessado pelo navegador). Roda como processo separado
 * de {@code McpApplication} (ver ADR-0006) - iniciar com:
 *
 * <pre>
 * java -cp drum-coach.jar dev.drumcoach.presentation.web.WebApplication
 * </pre>
 *
 * {@code scanBasePackages} e restrito de proposito a domain/application/infra +
 * presentation.web - nunca inclui presentation.mcp, para nao colidir em beans (ex.: dois
 * {@code OriginProvider} concorrentes) quando o outro processo existir.
 *
 * {@code @EnableJdbcRepositories} e explicito porque a auto-configuracao do Spring Boot
 * so escaneia repositorios Spring Data JDBC no pacote da classe {@code @SpringBootApplication}
 * (aqui, {@code presentation.web}), nao no {@code scanBasePackages} customizado - sem
 * isso os repositorios de {@code infra.persistence} nao sao encontrados.
 */
@SpringBootApplication(scanBasePackages = { "dev.drumcoach.domain", "dev.drumcoach.application", "dev.drumcoach.infra",
		"dev.drumcoach.presentation.web" })
@EnableJdbcRepositories(basePackages = "dev.drumcoach.infra.persistence")
public class WebApplication {

	public static void main(String[] args) {
		SpringApplication.run(WebApplication.class, args);
	}
}
