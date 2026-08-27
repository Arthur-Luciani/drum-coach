import { DrumPattern, DrumVoice } from '../models';

/**
 * Pontos de partida nomeados para o `pattern` tocavel de um exercicio `TOCA_JUNTO`
 * (ver ADR-0011). Espelham os presets do `mcp/` (`PatternPresets.java`, servidos pela
 * tool `list_pattern_presets`) - mesmos `key`/estrutura, pra que padrao criado pelo
 * Claude e padrao criado pela UI partam do mesmo lugar.
 *
 * Cada `pattern` passa na validacao estrutural do back (`total = bars * numerador *
 * stepsPerBeat`, steps em `[0, total)`, vozes no vocabulario, `sticking` so com voz
 * unica e comprimento `== total`, `accents` subconjunto dos `hits`).
 */
export interface PatternPreset {
  /** Chave curta e estavel (igual a do `mcp/`). */
  key: string;
  /** Rotulo exibido no botao de preset do editor. */
  label: string;
  pattern: DrumPattern;
}

/** Groove reto de semicolcheias, 1 compasso 4/4. */
const GROOVE_4_4: DrumPattern = {
  version: 1,
  timeSignature: [4, 4],
  stepsPerBeat: 4,
  tuplet: false,
  bars: 1,
  voices: ['hihat', 'snare', 'kick'],
  hits: {
    hihat: [0, 2, 4, 6, 8, 10, 12, 14],
    snare: [4, 12],
    kick: [0, 8],
  },
};

/** Paradiddle simples com acentos, 1 compasso 4/4 (16 semicolcheias na caixa). */
const PARADIDDLE: DrumPattern = {
  version: 1,
  timeSignature: [4, 4],
  stepsPerBeat: 4,
  tuplet: false,
  bars: 1,
  voices: ['snare'],
  hits: {
    snare: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15],
  },
  accents: {
    snare: [0, 4, 8, 12],
  },
  sticking: ['R', 'L', 'R', 'R', 'L', 'R', 'L', 'L', 'R', 'L', 'R', 'R', 'L', 'R', 'L', 'L'],
};

/** Shuffle em tercinas, 1 compasso 4/4 (stepsPerBeat 3, tuplet). */
const SHUFFLE: DrumPattern = {
  version: 1,
  timeSignature: [4, 4],
  stepsPerBeat: 3,
  tuplet: true,
  bars: 1,
  voices: ['hihat', 'snare', 'kick'],
  hits: {
    hihat: [0, 2, 3, 5, 6, 8, 9, 11],
    snare: [3, 9],
    kick: [0, 6],
  },
};

export const PATTERN_PRESETS: PatternPreset[] = [
  { key: 'groove-4-4', label: 'Groove reto de semicolcheias', pattern: GROOVE_4_4 },
  { key: 'paradiddle', label: 'Paradiddle com acentos', pattern: PARADIDDLE },
  { key: 'shuffle', label: 'Shuffle em tercinas', pattern: SHUFFLE },
];

/** Vocabulario fixo de vozes, na ordem de empilhamento canonica da pauta. Reexportado
 * daqui pro editor montar os toggles de voz sem reimportar do engraver. */
export const VOICE_VOCABULARY: DrumVoice[] = [
  'crash',
  'ride',
  'hihat',
  'hiTom',
  'midTom',
  'floorTom',
  'snare',
  'kick',
];

/** Padrao vazio de partida pro editor: 4/4, semicolcheias, 1 compasso, o kit basico de
 * chimbal/caixa/bumbo, nenhum hit ainda. */
export function emptyPattern(): DrumPattern {
  return {
    version: 1,
    timeSignature: [4, 4],
    stepsPerBeat: 4,
    tuplet: false,
    bars: 1,
    voices: ['hihat', 'snare', 'kick'],
    hits: {},
  };
}

/** Copia profunda de um preset - o editor nunca deve mutar o objeto compartilhado. */
export function clonePattern(pattern: DrumPattern): DrumPattern {
  return {
    ...pattern,
    timeSignature: [pattern.timeSignature[0], pattern.timeSignature[1]],
    voices: [...pattern.voices],
    hits: Object.fromEntries(
      Object.entries(pattern.hits).map(([v, steps]) => [v, [...(steps ?? [])]]),
    ),
    accents: pattern.accents
      ? Object.fromEntries(
          Object.entries(pattern.accents).map(([v, steps]) => [v, [...(steps ?? [])]]),
        )
      : undefined,
    sticking: pattern.sticking ? [...pattern.sticking] : undefined,
  };
}
