package dev.drumcoach.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import dev.drumcoach.domain.ExerciseKind;

/**
 * Value object do padrao tocavel de um exercicio {@code TOCA_JUNTO} (ver ADR-0011). Faz o
 * parse do JSON e a <strong>validacao estrutural</strong> (nada de valor ritmico / beam /
 * pausa - isso e derivado em runtime no front) e guarda a forma <strong>canonica
 * re-serializada</strong> ({@link #canonicalJson()}), nao o texto cru do request.
 *
 * Fica em {@code application} (e nao em {@code domain}) porque depende do Jackson - as
 * entidades de {@code domain} sao mantidas sem dependencias externas.
 *
 * Toda falha de validacao lanca {@link IllegalArgumentException} com mensagem clara; o
 * {@code GlobalExceptionHandler} ja converte IAE -> 400.
 */
public final class DrumPattern {

	/**
	 * Vocabulario fixo de vozes (ver ADR-0011). A ordem aqui e a ordem canonica de
	 * serializacao de {@code voices}/{@code hits}/{@code accents}.
	 */
	static final List<String> VOICE_VOCABULARY = List.of("crash", "ride", "hihat", "hiTom", "midTom", "floorTom",
			"snare", "kick");

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final String canonicalJson;

	private DrumPattern(String canonicalJson) {
		this.canonicalJson = canonicalJson;
	}

	/** JSON canonico re-serializado (o que deve ser persistido em {@code exercise.pattern}). */
	public String canonicalJson() {
		return canonicalJson;
	}

	/**
	 * Resolve o pattern canonico para o {@code kind} dado: retorna {@code null} quando nao
	 * ha pattern; rejeita pattern presente quando {@code kind != TOCA_JUNTO}; valida e
	 * devolve o JSON canonico quando ha pattern e {@code kind == TOCA_JUNTO}.
	 */
	public static String canonicalOrNull(ExerciseKind kind, String rawPattern) {
		if (rawPattern == null || rawPattern.isBlank()) {
			return null;
		}
		if (kind != ExerciseKind.TOCA_JUNTO) {
			throw new IllegalArgumentException("pattern so e permitido em exercicios do tipo TOCA_JUNTO");
		}
		return parse(rawPattern).canonicalJson();
	}

	/** Faz o parse + validacao estrutural do JSON cru e devolve o pattern canonico. */
	public static DrumPattern parse(String rawJson) {
		if (rawJson == null || rawJson.isBlank()) {
			throw new IllegalArgumentException("pattern ausente");
		}
		JsonNode root;
		try {
			root = MAPPER.readTree(rawJson);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("pattern nao e um JSON valido: " + e.getMessage());
		}
		if (root == null || !root.isObject()) {
			throw new IllegalArgumentException("pattern deve ser um objeto JSON");
		}

		int version = requirePositiveInt(root.get("version"), "version");
		if (version != 1) {
			throw new IllegalArgumentException("pattern.version nao suportada: " + version + " (esperado 1)");
		}

		JsonNode tsNode = root.get("timeSignature");
		if (tsNode == null || !tsNode.isArray() || tsNode.size() != 2) {
			throw new IllegalArgumentException(
					"pattern.timeSignature deve ser um array [numerador, denominador]");
		}
		int tsNum = requirePositiveInt(tsNode.get(0), "timeSignature[0]");
		int tsDen = requirePositiveInt(tsNode.get(1), "timeSignature[1]");

		int stepsPerBeat = requirePositiveInt(root.get("stepsPerBeat"), "stepsPerBeat");

		JsonNode tupletNode = root.get("tuplet");
		if (tupletNode == null || !tupletNode.isBoolean()) {
			throw new IllegalArgumentException("pattern.tuplet deve ser boolean");
		}
		boolean tuplet = tupletNode.booleanValue();

		int bars = requirePositiveInt(root.get("bars"), "bars");

		int total = bars * tsNum * stepsPerBeat;

		// voices: array nao-vazio, subconjunto do vocabulario, sem repeticao.
		JsonNode voicesNode = root.get("voices");
		if (voicesNode == null || !voicesNode.isArray() || voicesNode.isEmpty()) {
			throw new IllegalArgumentException("pattern.voices deve ser um array nao-vazio");
		}
		List<String> voices = new ArrayList<>();
		Set<String> voiceSet = new LinkedHashSet<>();
		for (JsonNode v : voicesNode) {
			if (!v.isString()) {
				throw new IllegalArgumentException("pattern.voices deve conter apenas strings");
			}
			String voice = v.stringValue();
			if (!VOICE_VOCABULARY.contains(voice)) {
				throw new IllegalArgumentException("pattern.voices contem voz fora do vocabulario: " + voice);
			}
			if (!voiceSet.add(voice)) {
				throw new IllegalArgumentException("pattern.voices contem voz repetida: " + voice);
			}
			voices.add(voice);
		}

		// hits: objeto; chaves subconjunto de voices; valores = array de inteiros em
		// [0, total), sem negativos nem duplicados.
		JsonNode hitsNode = root.get("hits");
		if (hitsNode == null || !hitsNode.isObject()) {
			throw new IllegalArgumentException("pattern.hits deve ser um objeto");
		}
		Map<String, List<Integer>> hits = new LinkedHashMap<>();
		for (Map.Entry<String, JsonNode> entry : hitsNode.properties()) {
			String voice = entry.getKey();
			if (!voiceSet.contains(voice)) {
				throw new IllegalArgumentException(
						"pattern.hits referencia voz que nao esta em voices: " + voice);
			}
			hits.put(voice, readIndexArray(entry.getValue(), "hits." + voice, 0, total));
		}

		// accents (opcional): objeto; chaves subconjunto de voices; cada indice tambem
		// presente em hits daquela voz.
		JsonNode accentsNode = root.get("accents");
		Map<String, List<Integer>> accents = new LinkedHashMap<>();
		if (accentsNode != null && !accentsNode.isNull()) {
			if (!accentsNode.isObject()) {
				throw new IllegalArgumentException("pattern.accents deve ser um objeto");
			}
			for (Map.Entry<String, JsonNode> entry : accentsNode.properties()) {
				String voice = entry.getKey();
				if (!voiceSet.contains(voice)) {
					throw new IllegalArgumentException(
							"pattern.accents referencia voz que nao esta em voices: " + voice);
				}
				List<Integer> indices = readIndexArray(entry.getValue(), "accents." + voice, 0, total);
				List<Integer> hitsForVoice = hits.getOrDefault(voice, List.of());
				for (int idx : indices) {
					if (!hitsForVoice.contains(idx)) {
						throw new IllegalArgumentException("pattern.accents." + voice + " marca acento no indice "
								+ idx + " que nao tem hit correspondente");
					}
				}
				accents.put(voice, indices);
			}
		}

		// sticking (opcional): so valido com voz unica; array de comprimento total;
		// entradas em {"R","L"}.
		List<String> sticking = null;
		JsonNode stickingNode = root.get("sticking");
		if (stickingNode != null && !stickingNode.isNull()) {
			if (!stickingNode.isArray()) {
				throw new IllegalArgumentException("pattern.sticking deve ser um array");
			}
			if (voices.size() != 1) {
				throw new IllegalArgumentException(
						"pattern.sticking so e valido para padrao de voz unica (rudimento)");
			}
			if (stickingNode.size() != total) {
				throw new IllegalArgumentException("pattern.sticking deve ter comprimento " + total
						+ " (bars * numerador * stepsPerBeat), mas tem " + stickingNode.size());
			}
			sticking = new ArrayList<>();
			for (JsonNode s : stickingNode) {
				String value = s.isString() ? s.stringValue() : null;
				if (!"R".equals(value) && !"L".equals(value)) {
					throw new IllegalArgumentException("pattern.sticking deve conter apenas \"R\" ou \"L\"");
				}
				sticking.add(value);
			}
		}

		return new DrumPattern(serializeCanonical(tsNum, tsDen, stepsPerBeat, tuplet, bars, voices, hits, accents,
				accentsNode != null && !accentsNode.isNull(), sticking));
	}

	private static List<Integer> readIndexArray(JsonNode node, String label, int minInclusive, int maxExclusive) {
		if (node == null || !node.isArray()) {
			throw new IllegalArgumentException("pattern." + label + " deve ser um array de inteiros");
		}
		Set<Integer> sorted = new TreeSet<>();
		for (JsonNode idxNode : node) {
			if (!idxNode.isIntegralNumber() || !idxNode.canConvertToInt()) {
				throw new IllegalArgumentException("pattern." + label + " deve conter apenas inteiros");
			}
			int idx = idxNode.intValue();
			if (idx < minInclusive || idx >= maxExclusive) {
				throw new IllegalArgumentException("pattern." + label + " tem indice fora do intervalo ["
						+ minInclusive + ", " + maxExclusive + "): " + idx);
			}
			if (!sorted.add(idx)) {
				throw new IllegalArgumentException("pattern." + label + " tem indice duplicado: " + idx);
			}
		}
		return new ArrayList<>(sorted);
	}

	private static int requirePositiveInt(JsonNode node, String label) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
			throw new IllegalArgumentException("pattern." + label + " deve ser um inteiro");
		}
		int value = node.intValue();
		if (value <= 0) {
			throw new IllegalArgumentException("pattern." + label + " deve ser > 0, mas e " + value);
		}
		return value;
	}

	private static String serializeCanonical(int tsNum, int tsDen, int stepsPerBeat, boolean tuplet, int bars,
			List<String> voices, Map<String, List<Integer>> hits, Map<String, List<Integer>> accents,
			boolean includeAccents, List<String> sticking) {
		ObjectNode canonical = MAPPER.createObjectNode();
		canonical.put("version", 1);
		ArrayNode ts = canonical.putArray("timeSignature");
		ts.add(tsNum);
		ts.add(tsDen);
		canonical.put("stepsPerBeat", stepsPerBeat);
		canonical.put("tuplet", tuplet);
		canonical.put("bars", bars);
		ArrayNode voicesArr = canonical.putArray("voices");
		voices.forEach(voicesArr::add);
		ObjectNode hitsObj = canonical.putObject("hits");
		for (String voice : voices) {
			List<Integer> indices = hits.get(voice);
			if (indices != null) {
				ArrayNode arr = hitsObj.putArray(voice);
				indices.forEach(arr::add);
			}
		}
		if (includeAccents) {
			ObjectNode accObj = canonical.putObject("accents");
			for (String voice : voices) {
				List<Integer> indices = accents.get(voice);
				if (indices != null) {
					ArrayNode arr = accObj.putArray(voice);
					indices.forEach(arr::add);
				}
			}
		}
		if (sticking != null) {
			ArrayNode stArr = canonical.putArray("sticking");
			sticking.forEach(stArr::add);
		}
		try {
			return MAPPER.writeValueAsString(canonical);
		}
		catch (JacksonException e) {
			throw new IllegalArgumentException("falha ao serializar pattern canonico: " + e.getMessage());
		}
	}
}
