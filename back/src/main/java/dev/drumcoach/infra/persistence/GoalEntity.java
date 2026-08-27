package dev.drumcoach.infra.persistence;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import dev.drumcoach.domain.GoalStatus;
import dev.drumcoach.domain.Origin;

/**
 * Registro de persistencia de {@link dev.drumcoach.domain.Goal}, mapeado via Spring
 * Data JDBC para a tabela {@code goal}. Nunca e exposto fora de {@code infra} - o
 * domain.Goal e mapeado de/para esta classe por {@link GoalMapper}.
 *
 * Record (imutavel) de proposito: Spring Data JDBC suporta agregados imutaveis
 * nativamente, incluindo geracao de PK (apos o insert, ele reconstroi uma nova instancia
 * chamando este mesmo construtor canonico com o id gerado). Assim {@code infra} nao
 * precisa de setters so para o driver de persistencia preencher a entidade.
 *
 * {@code inFocus}: SQLite nao tem tipo BOOLEAN nativo (coluna {@code in_focus INTEGER}) -
 * mapeado direto para {@code boolean} Java sem conversor customizado, ja que o driver
 * JDBC (org.xerial:sqlite-jdbc) e o Spring Data JDBC ja lidam com essa conversao 0/1 <->
 * boolean nativamente (ao contrario de {@code Instant}/{@code LocalDate}/enums, que
 * precisam de conversores explicitos em {@code JdbcConfig} - ver risco tecnico #4).
 */
@Table("goal")
public record GoalEntity(@Id Long id, String title, String description, LocalDate targetDate, GoalStatus status,
		String targetMetric, boolean inFocus, Origin createdBy, Origin lastModifiedBy, Instant createdAt,
		Instant updatedAt) {
}
