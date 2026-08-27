package dev.drumcoach.infra.config;

import org.springframework.data.jdbc.core.dialect.JdbcArrayColumns;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.relational.core.dialect.AnsiDialect;

/**
 * Risco tecnico #2 do plano de implementacao: Spring Data JDBC (versao usada pelo Boot
 * 4.1) nao tem um dialect de primeira classe para SQLite - {@code DialectResolver} nao
 * reconhece o produto JDBC "sqlite" e falha com {@code NoDialectException} se nenhum
 * bean de {@link JdbcDialect} for fornecido.
 *
 * Validado empiricamente (Fase 0): as operacoes basicas usadas por este app (insert com
 * PK autoincrement, select por id, select all) funcionam corretamente simplesmente
 * reaproveitando {@link AnsiDialect} - nao foi necessario recorrer a lib comunitaria
 * {@code io.github.jamoamo:spring-data-jdbc-sqlite}. So precisou de um adapter minimo
 * porque o tipo de bean esperado nesta versao e {@link JdbcDialect} (que estende
 * {@code Dialect}), nao {@code Dialect} puro.
 */
final class SqliteJdbcDialect extends AnsiDialect implements JdbcDialect {

	static final SqliteJdbcDialect INSTANCE = new SqliteJdbcDialect();

	private SqliteJdbcDialect() {
	}

	@Override
	public JdbcArrayColumns getArraySupport() {
		// SQLite nao tem tipo de coluna array - nao usado pela entidade Goal.
		return JdbcArrayColumns.unsupported();
	}
}
