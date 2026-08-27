package dev.drumcoach.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import dev.drumcoach.domain.ExerciseKind;

/**
 * Testes unitarios da validacao estrutural do {@code pattern} tocavel (ADR-0011): um caso
 * valido completo + um teste por modo de falha.
 */
class DrumPatternTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String VALID = """
			{
			  "version": 1,
			  "timeSignature": [4, 4],
			  "stepsPerBeat": 4,
			  "tuplet": false,
			  "bars": 2,
			  "voices": ["hihat", "snare", "kick"],
			  "hits": { "hihat": [0,2,4,6,8,10,12,14], "snare": [4,12], "kick": [0,6,8,14] },
			  "accents": { "snare": [4] }
			}
			""";

	@Test
	void acceptsAFullValidPatternAndStoresCanonicalForm() {
		DrumPattern pattern = DrumPattern.parse(VALID);

		assertThat(pattern.canonicalJson()).isNotBlank();
		// forma canonica: chaves em ordem fixa, sem espacos, arrays de hits ordenados.
		assertThat(pattern.canonicalJson()).isEqualTo("{\"version\":1,\"timeSignature\":[4,4],"
				+ "\"stepsPerBeat\":4,\"tuplet\":false,\"bars\":2,\"voices\":[\"hihat\",\"snare\",\"kick\"],"
				+ "\"hits\":{\"hihat\":[0,2,4,6,8,10,12,14],\"snare\":[4,12],\"kick\":[0,6,8,14]},"
				+ "\"accents\":{\"snare\":[4]}}");
	}

	@Test
	void reserializesCanonically_reordersKeysAndSortsHitIndices() {
		String messy = """
				{ "bars": 1, "voices": ["snare"], "stepsPerBeat": 2, "timeSignature": [2, 4],
				  "tuplet": true, "version": 1, "hits": { "snare": [3, 0, 2] } }
				""";

		assertThat(DrumPattern.parse(messy).canonicalJson()).isEqualTo("{\"version\":1,\"timeSignature\":[2,4],"
				+ "\"stepsPerBeat\":2,\"tuplet\":true,\"bars\":1,\"voices\":[\"snare\"],\"hits\":{\"snare\":[0,2,3]}}");
	}

	@Test
	void canonicalOrNull_returnsNullWhenNoPattern() {
		assertThat(DrumPattern.canonicalOrNull(ExerciseKind.TOCA_JUNTO, null)).isNull();
		assertThat(DrumPattern.canonicalOrNull(ExerciseKind.TOCA_JUNTO, "  ")).isNull();
	}

	@Test
	void canonicalOrNull_rejectsPatternWhenKindIsNotTocaJunto() {
		assertThatThrownBy(() -> DrumPattern.canonicalOrNull(ExerciseKind.TRANSCRICAO, VALID))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("TOCA_JUNTO");
	}

	@Test
	void rejectsMalformedJson() {
		assertThatThrownBy(() -> DrumPattern.parse("{ not json "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("JSON");
	}

	@Test
	void rejectsNonObjectRoot() {
		assertThatThrownBy(() -> DrumPattern.parse("[1,2,3]")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("objeto JSON");
	}

	@Test
	void rejectsUnsupportedVersion() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"version\": 1", "\"version\": 2")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("version");
	}

	@Test
	void rejectsInvalidTimeSignature() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("[4, 4]", "[4, 0]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("timeSignature");

		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("[4, 4]", "[4]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("timeSignature");
	}

	@Test
	void rejectsNonPositiveStepsPerBeat() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"stepsPerBeat\": 4", "\"stepsPerBeat\": 0")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("stepsPerBeat");
	}

	@Test
	void rejectsNonBooleanTuplet() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"tuplet\": false", "\"tuplet\": \"no\"")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("tuplet");
	}

	@Test
	void rejectsNonPositiveBars() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"bars\": 2", "\"bars\": 0")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("bars");
	}

	@Test
	void rejectsEmptyVoices() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("[\"hihat\", \"snare\", \"kick\"]", "[]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("voices");
	}

	@Test
	void rejectsVoiceOutOfVocabulary() {
		assertThatThrownBy(
				() -> DrumPattern.parse(VALID.replace("[\"hihat\", \"snare\", \"kick\"]", "[\"cowbell\", \"snare\"]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("vocabulario");
	}

	@Test
	void rejectsRepeatedVoice() {
		assertThatThrownBy(() -> DrumPattern
			.parse(VALID.replace("[\"hihat\", \"snare\", \"kick\"]", "[\"snare\", \"snare\"]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("repetida");
	}

	@Test
	void rejectsHitsForVoiceNotInVoices() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"snare\": [4,12]", "\"ride\": [4,12]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("voices");
	}

	@Test
	void rejectsHitOutOfRange() {
		// total = 2 * 4 * 4 = 32; indice 32 e fora de [0, 32)
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"snare\": [4,12]", "\"snare\": [4,32]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("intervalo");
	}

	@Test
	void rejectsNegativeHit() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"snare\": [4,12]", "\"snare\": [-1,12]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("intervalo");
	}

	@Test
	void rejectsDuplicateHitIndex() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"snare\": [4,12]", "\"snare\": [4,4,12]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("duplicado");
	}

	@Test
	void rejectsNonIntegerHit() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"snare\": [4,12]", "\"snare\": [4.5,12]")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("inteiros");
	}

	@Test
	void rejectsAccentWithoutMatchingHit() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"accents\": { \"snare\": [4] }",
				"\"accents\": { \"snare\": [8] }")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("hit correspondente");
	}

	@Test
	void rejectsAccentForVoiceNotInVoices() {
		assertThatThrownBy(() -> DrumPattern.parse(VALID.replace("\"accents\": { \"snare\": [4] }",
				"\"accents\": { \"ride\": [4] }")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("voices");
	}

	@Test
	void acceptsValidSingleVoiceSticking() {
		String rudiment = """
				{ "version": 1, "timeSignature": [4, 4], "stepsPerBeat": 2, "tuplet": false, "bars": 1,
				  "voices": ["snare"], "hits": { "snare": [0,1,2,3,4,5,6,7] },
				  "sticking": ["R","L","R","R","L","R","L","L"] }
				""";

		assertThatCode(() -> DrumPattern.parse(rudiment)).doesNotThrowAnyException();
		assertThat(DrumPattern.parse(rudiment).canonicalJson()).contains("\"sticking\":[\"R\",\"L\",\"R\",\"R\"");
	}

	@Test
	void rejectsStickingWithMoreThanOneVoice() {
		String twoVoices = """
				{ "version": 1, "timeSignature": [4, 4], "stepsPerBeat": 2, "tuplet": false, "bars": 1,
				  "voices": ["snare", "kick"], "hits": { "snare": [0,2,4,6], "kick": [1,3,5,7] },
				  "sticking": ["R","L","R","R","L","R","L","L"] }
				""";

		assertThatThrownBy(() -> DrumPattern.parse(twoVoices)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("voz unica");
	}

	@Test
	void rejectsStickingWithWrongLength() {
		String wrongLen = """
				{ "version": 1, "timeSignature": [4, 4], "stepsPerBeat": 2, "tuplet": false, "bars": 1,
				  "voices": ["snare"], "hits": { "snare": [0,1,2,3] }, "sticking": ["R","L","R"] }
				""";

		assertThatThrownBy(() -> DrumPattern.parse(wrongLen)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("comprimento");
	}

	@Test
	void rejectsStickingWithInvalidEntry() {
		String badEntry = """
				{ "version": 1, "timeSignature": [4, 4], "stepsPerBeat": 2, "tuplet": false, "bars": 1,
				  "voices": ["snare"], "hits": { "snare": [0,1,2,3,4,5,6,7] },
				  "sticking": ["R","L","R","R","L","R","L","X"] }
				""";

		assertThatThrownBy(() -> DrumPattern.parse(badEntry)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("R");
	}

	@Test
	void acceptsPatternWithoutOptionalAccentsOrSticking() {
		String minimal = """
				{ "version": 1, "timeSignature": [4, 4], "stepsPerBeat": 4, "tuplet": false, "bars": 1,
				  "voices": ["kick"], "hits": { "kick": [0, 8] } }
				""";

		assertThatCode(() -> DrumPattern.parse(minimal)).doesNotThrowAnyException();
		assertThat(DrumPattern.parse(minimal).canonicalJson()).doesNotContain("accents").doesNotContain("sticking");
	}

	@Test
	void canonicalJsonIsItselfReparseable() {
		String canonical = DrumPattern.parse(VALID).canonicalJson();
		assertThatCode(() -> MAPPER.readTree(canonical)).doesNotThrowAnyException();
		assertThat(DrumPattern.parse(canonical).canonicalJson()).isEqualTo(canonical);
	}
}
