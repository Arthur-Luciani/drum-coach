package dev.drumcoach.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pontos de partida nomeados para o {@code pattern} tocavel de um exercicio
 * {@code TOCA_JUNTO} (ver ADR-0011). Nao ha endpoint no {@code back} para isto - os presets
 * ficam embutidos no proprio proxy MCP, servidos pela tool {@code list_pattern_presets},
 * para o Claude nao precisar montar todo padrao do zero.
 *
 * <p>
 * Cada {@code pattern} aqui e um objeto ({@code LinkedHashMap}, ordem canonica de chaves)
 * que passa na validacao estrutural de {@code application.DrumPattern} do back:
 * {@code total = bars * numerador * stepsPerBeat}, steps em {@code [0, total)}, vozes no
 * vocabulario, {@code sticking} so com voz unica e comprimento {@code == total},
 * {@code accents} subconjunto dos {@code hits}.
 */
final class PatternPresets {

	/** Um preset: nome curto, descricao e o documento do padrao pronto para uso. */
	record Preset(String name, String description, Map<String, Object> pattern) {
	}

	private PatternPresets() {
	}

	static List<Preset> all() {
		return List.of(groove44(), paradiddle(), shuffle());
	}

	private static Preset groove44() {
		Map<String, Object> hits = new LinkedHashMap<>();
		hits.put("hihat", List.of(0, 2, 4, 6, 8, 10, 12, 14));
		hits.put("snare", List.of(4, 12));
		hits.put("kick", List.of(0, 8));
		return new Preset("groove-4-4", "Groove reto de semicolcheias, 1 compasso",
				pattern(new int[] { 4, 4 }, 4, false, 1, List.of("hihat", "snare", "kick"), hits, null, null));
	}

	private static Preset paradiddle() {
		Map<String, Object> hits = new LinkedHashMap<>();
		hits.put("snare", List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
		Map<String, Object> accents = new LinkedHashMap<>();
		accents.put("snare", List.of(0, 4, 8, 12));
		List<String> sticking = List.of("R", "L", "R", "R", "L", "R", "L", "L", "R", "L", "R", "R", "L", "R", "L", "L");
		return new Preset("paradiddle", "Paradiddle simples com acentos, 1 compasso",
				pattern(new int[] { 4, 4 }, 4, false, 1, List.of("snare"), hits, accents, sticking));
	}

	private static Preset shuffle() {
		Map<String, Object> hits = new LinkedHashMap<>();
		hits.put("hihat", List.of(0, 2, 3, 5, 6, 8, 9, 11));
		hits.put("snare", List.of(3, 9));
		hits.put("kick", List.of(0, 6));
		return new Preset("shuffle", "Shuffle em tercinas, 1 compasso",
				pattern(new int[] { 4, 4 }, 3, true, 1, List.of("hihat", "snare", "kick"), hits, null, null));
	}

	private static Map<String, Object> pattern(int[] timeSignature, int stepsPerBeat, boolean tuplet, int bars,
			List<String> voices, Map<String, Object> hits, Map<String, Object> accents, List<String> sticking) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("version", 1);
		p.put("timeSignature", List.of(timeSignature[0], timeSignature[1]));
		p.put("stepsPerBeat", stepsPerBeat);
		p.put("tuplet", tuplet);
		p.put("bars", bars);
		p.put("voices", voices);
		p.put("hits", hits);
		if (accents != null) {
			p.put("accents", accents);
		}
		if (sticking != null) {
			p.put("sticking", sticking);
		}
		return p;
	}
}
