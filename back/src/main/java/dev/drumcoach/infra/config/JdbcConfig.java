package dev.drumcoach.infra.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;

import dev.drumcoach.domain.ExerciseKind;
import dev.drumcoach.domain.GoalStatus;
import dev.drumcoach.domain.Origin;
import dev.drumcoach.domain.RepertoireItemStatus;
import dev.drumcoach.domain.VideoSourceType;

/**
 * Conversoes explicitas Spring Data JDBC <-> SQLite (risco #4 do plano de
 * implementacao): SQLite nao tem tipo nativo de data/enum/boolean, entao {@code Instant},
 * {@code LocalDate}, os enums de dominio e os campos {@code boolean} (ex.:
 * {@code goal.in_focus}) sao sempre armazenados como {@code TEXT}/{@code INTEGER}, nunca
 * por conversao automatica do driver - validado empiricamente (o driver
 * org.xerial:sqlite-jdbc devolve colunas INTEGER como {@code java.lang.Integer}, e o
 * Spring Data JDBC nao converte {@code Integer -> boolean} sem um conversor explicito).
 */
@Configuration
public class JdbcConfig {

	/**
	 * Ver {@link SqliteJdbcDialect} - risco tecnico #2 (dialect SQLite).
	 */
	@Bean
	public JdbcDialect jdbcDialect() {
		return SqliteJdbcDialect.INSTANCE;
	}

	@Bean
	public JdbcCustomConversions jdbcCustomConversions() {
		return new JdbcCustomConversions(List.of(InstantToTextConverter.INSTANCE, TextToInstantConverter.INSTANCE,
				LocalDateToTextConverter.INSTANCE, TextToLocalDateConverter.INSTANCE,
				GoalStatusToTextConverter.INSTANCE, TextToGoalStatusConverter.INSTANCE,
				OriginToTextConverter.INSTANCE, TextToOriginConverter.INSTANCE,
				VideoSourceTypeToTextConverter.INSTANCE, TextToVideoSourceTypeConverter.INSTANCE,
				RepertoireItemStatusToTextConverter.INSTANCE, TextToRepertoireItemStatusConverter.INSTANCE,
				ExerciseKindToTextConverter.INSTANCE, TextToExerciseKindConverter.INSTANCE,
				BooleanToIntegerConverter.INSTANCE, IntegerToBooleanConverter.INSTANCE));
	}

	@WritingConverter
	enum InstantToTextConverter implements Converter<Instant, String> {

		INSTANCE;

		@Override
		public String convert(Instant source) {
			return DateTimeFormatter.ISO_INSTANT.format(source);
		}
	}

	@ReadingConverter
	enum TextToInstantConverter implements Converter<String, Instant> {

		INSTANCE;

		@Override
		public Instant convert(String source) {
			return Instant.parse(source);
		}
	}

	@WritingConverter
	enum LocalDateToTextConverter implements Converter<LocalDate, String> {

		INSTANCE;

		@Override
		public String convert(LocalDate source) {
			return source.toString();
		}
	}

	@ReadingConverter
	enum TextToLocalDateConverter implements Converter<String, LocalDate> {

		INSTANCE;

		@Override
		public LocalDate convert(String source) {
			return LocalDate.parse(source);
		}
	}

	@WritingConverter
	enum GoalStatusToTextConverter implements Converter<GoalStatus, String> {

		INSTANCE;

		@Override
		public String convert(GoalStatus source) {
			return source.name();
		}
	}

	@ReadingConverter
	enum TextToGoalStatusConverter implements Converter<String, GoalStatus> {

		INSTANCE;

		@Override
		public GoalStatus convert(String source) {
			return GoalStatus.valueOf(source);
		}
	}

	@WritingConverter
	enum OriginToTextConverter implements Converter<Origin, String> {

		INSTANCE;

		@Override
		public String convert(Origin source) {
			return source.name();
		}
	}

	@ReadingConverter
	enum TextToOriginConverter implements Converter<String, Origin> {

		INSTANCE;

		@Override
		public Origin convert(String source) {
			return Origin.valueOf(source);
		}
	}

	@WritingConverter
	enum VideoSourceTypeToTextConverter implements Converter<VideoSourceType, String> {

		INSTANCE;

		@Override
		public String convert(VideoSourceType source) {
			return source.name();
		}
	}

	@ReadingConverter
	enum TextToVideoSourceTypeConverter implements Converter<String, VideoSourceType> {

		INSTANCE;

		@Override
		public VideoSourceType convert(String source) {
			return VideoSourceType.valueOf(source);
		}
	}

	@WritingConverter
	enum RepertoireItemStatusToTextConverter implements Converter<RepertoireItemStatus, String> {

		INSTANCE;

		@Override
		public String convert(RepertoireItemStatus source) {
			return source.name();
		}
	}

	@ReadingConverter
	enum TextToRepertoireItemStatusConverter implements Converter<String, RepertoireItemStatus> {

		INSTANCE;

		@Override
		public RepertoireItemStatus convert(String source) {
			return RepertoireItemStatus.valueOf(source);
		}
	}

	@WritingConverter
	enum ExerciseKindToTextConverter implements Converter<ExerciseKind, String> {

		INSTANCE;

		@Override
		public String convert(ExerciseKind source) {
			return source.name();
		}
	}

	@ReadingConverter
	enum TextToExerciseKindConverter implements Converter<String, ExerciseKind> {

		INSTANCE;

		@Override
		public ExerciseKind convert(String source) {
			return ExerciseKind.valueOf(source);
		}
	}

	@WritingConverter
	enum BooleanToIntegerConverter implements Converter<Boolean, Integer> {

		INSTANCE;

		@Override
		public Integer convert(Boolean source) {
			return source ? 1 : 0;
		}
	}

	@ReadingConverter
	enum IntegerToBooleanConverter implements Converter<Integer, Boolean> {

		INSTANCE;

		@Override
		public Boolean convert(Integer source) {
			return source != 0;
		}
	}
}
