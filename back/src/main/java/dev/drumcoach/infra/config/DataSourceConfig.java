package dev.drumcoach.infra.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * DataSource SQLite compartilhado pelos dois entry points (mesmo arquivo, sem servidor
 * de banco). Risco tecnico #5 do plano de implementacao: concorrencia entre os dois
 * processos no mesmo arquivo - resolvido com {@code journal_mode=WAL} e um
 * {@code busy_timeout} generoso (5s) em vez de lock aplicacional, adequado ao volume de
 * escrita baixo de um app single-user.
 *
 * Risco #3: geracao de PK via {@code getGeneratedKeys()} no driver
 * {@code org.xerial:sqlite-jdbc} - habilitada explicitamente via
 * {@code SQLiteConfig#setGetGeneratedKeys(true)} (pragma "fake" especifica do driver
 * para compatibilidade JDBC). Validado empiricamente pelo insert de {@code GoalEntity}
 * via Spring Data JDBC.
 */
@Configuration
@EnableConfigurationProperties(DrumCoachDataProperties.class)
public class DataSourceConfig {

	private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

	@Bean
	public DataSource dataSource(DrumCoachDataProperties properties) {
		Path dbPath = properties.resolveDbPath();
		try {
			Files.createDirectories(dbPath.getParent());
		}
		catch (IOException e) {
			throw new UncheckedIOException("Nao foi possivel criar o diretorio de dados: " + dbPath.getParent(), e);
		}

		SQLiteConfig sqliteConfig = new SQLiteConfig();
		sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
		sqliteConfig.setBusyTimeout(5000);
		sqliteConfig.setGetGeneratedKeys(true);

		SQLiteDataSource dataSource = new SQLiteDataSource(sqliteConfig);
		dataSource.setUrl("jdbc:sqlite:" + dbPath);

		log.info("SQLite database file: {}", dbPath);
		return dataSource;
	}
}
