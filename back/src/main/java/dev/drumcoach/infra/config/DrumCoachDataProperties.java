package dev.drumcoach.infra.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Local do arquivo SQLite do caderno. Por padrao fica fora da pasta de instalacao do
 * jar (evita perda de dados em upgrades/reinstalacao) - ver README.
 *
 * Configuravel via {@code drumcoach.data.db-path} em {@code application.yml} ou pela
 * variavel de ambiente/propriedade JVM equivalente. Record com constructor binding
 * (suportado nativamente pelo Spring Boot) - sem setters.
 */
@ConfigurationProperties(prefix = "drumcoach.data")
public record DrumCoachDataProperties(String dbPath) {

	public DrumCoachDataProperties {
		if (dbPath == null || dbPath.isBlank()) {
			dbPath = defaultDbPath();
		}
	}

	public Path resolveDbPath() {
		return Paths.get(dbPath).toAbsolutePath();
	}

	private static String defaultDbPath() {
		String userHome = System.getProperty("user.home");
		return Paths.get(userHome, ".drum-coach", "drum-coach.db").toString();
	}
}
