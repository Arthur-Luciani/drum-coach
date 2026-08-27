import { describe, expect, it } from 'vitest';

import { DrumPattern } from '../models';
import {
  engrave,
  figureOf,
  layoutBeat,
  STEM_LEN,
  toggleHit,
  valueOf,
} from './pattern-engraver';

/** Base minima de um pattern - cada teste sobrescreve o que precisa. */
function pat(over: Partial<DrumPattern>): DrumPattern {
  return {
    version: 1,
    timeSignature: [4, 4],
    stepsPerBeat: 4,
    tuplet: false,
    bars: 1,
    voices: ['hihat', 'snare', 'kick'],
    hits: {},
    ...over,
  };
}

describe('valueOf / figureOf', () => {
  it('gap >= sub -> semininma (sem beam, sem flag)', () => {
    expect(valueOf(4, 4, false)).toEqual({ span: 4, beams: 0, flags: 0 });
  });

  it('gap de colcheia -> 1 beam / 1 flag; gap de semicolcheia -> 2 / 2', () => {
    expect(valueOf(2, 4, false)).toEqual({ span: 2, beams: 1, flags: 1 });
    expect(valueOf(1, 4, false)).toEqual({ span: 1, beams: 2, flags: 2 });
  });

  it('tuplet: gap 1 -> 1 beam, gap >= 2 -> sem beam (nunca semicolcheia)', () => {
    expect(valueOf(1, 3, true)).toEqual({ span: 1, beams: 1, flags: 1 });
    expect(valueOf(2, 3, true)).toEqual({ span: 2, beams: 0, flags: 0 });
  });

  it('figureOf e o mesmo que valueOf', () => {
    expect(figureOf).toBe(valueOf);
  });
});

describe('layoutBeat', () => {
  it('tempo inteiro em silencio -> uma pausa de semininma', () => {
    const { notes, rests } = layoutBeat([], 4, false);
    expect(notes).toHaveLength(0);
    expect(rests).toEqual([{ slot: 0, span: 4, kind: 'quarter' }]);
  });

  it('quatro semicolcheias -> quatro notas, cada uma com 2 beams', () => {
    const { notes, rests } = layoutBeat([0, 1, 2, 3], 4, false);
    expect(notes).toHaveLength(4);
    expect(notes.every((n) => n.v.beams === 2)).toBe(true);
    expect(rests).toHaveLength(0);
  });
});

describe('engrave - groove reto (colcheias/semicolcheias, beam por tempo)', () => {
  const groove = pat({
    stepsPerBeat: 4,
    bars: 1,
    voices: ['hihat', 'snare', 'kick'],
    hits: {
      hihat: [0, 2, 4, 6, 8, 10, 12, 14],
      snare: [4, 12],
      kick: [0, 8],
    },
  });

  // Chimbal isolado numa colcheia de contratempo (um unico beamable no tempo).
  const loneEighth = pat({
    stepsPerBeat: 4,
    bars: 1,
    voices: ['snare'],
    hits: { snare: [2] },
  });

  it('total = bars * beats * stepsPerBeat', () => {
    expect(engrave(groove).total).toBe(16);
  });

  it('conta as cabecas de nota: 8 hihat (x) + 2 snare (o) + 2 kick (o)', () => {
    const m = engrave(groove);
    expect(m.heads.filter((h) => h.voice === 'hihat')).toHaveLength(8);
    expect(m.heads.filter((h) => h.voice === 'hihat').every((h) => h.headType === 'x')).toBe(true);
    expect(m.heads.filter((h) => h.voice === 'snare')).toHaveLength(2);
    expect(m.heads.filter((h) => h.voice === 'kick')).toHaveLength(2);
  });

  it('cada tempo com 2 colcheias de chimbal gera uma barra de ligadura primaria; sem secundarias', () => {
    const m = engrave(groove);
    const primaries = m.beams.filter((b) => !b.secondary);
    // 4 tempos de mao (chimbal em pares) + 0 de pe (kick isolado por tempo -> flag)
    expect(primaries.length).toBe(4);
    expect(m.beams.some((b) => b.secondary)).toBe(false);
  });

  it('mao = haste pra cima, kick = haste pra baixo', () => {
    const m = engrave(groove);
    expect(m.stems.length).toBeGreaterThan(0);
    // ha hastes das duas direcoes
    expect(m.stems.some((s) => s.dir === 'up')).toBe(true);
    expect(m.stems.some((s) => s.dir === 'down')).toBe(true);
  });

  it('groove todo ligado por tempo (ou semininma) -> nenhuma bandeirola', () => {
    expect(engrave(groove).flags).toHaveLength(0);
  });

  it('colcheia isolada num tempo -> uma bandeirola (haste pra cima), sem beam', () => {
    const m = engrave(loneEighth);
    expect(m.flags).toHaveLength(1);
    expect(m.flags[0].dir).toBe('up');
    expect(m.beams).toHaveLength(0);
  });

  it('barra de ligadura POR GRUPO: ~STEM_LEN acima da cabeca do chimbal, nao numa linha fixa distante', () => {
    const m = engrave(groove);
    const hihatY = m.heads.find((h) => h.voice === 'hihat')!.y;
    const primary = m.beams.find((b) => !b.secondary)!;
    // a barra fica logo acima das cabecas do grupo (dentro de ~4 px de hihatY - STEM_LEN)
    expect(Math.abs(primary.y - (hihatY - STEM_LEN))).toBeLessThanOrEqual(4);
    // haste da nota mais alta do grupo tem ~STEM_LEN de comprimento (nao 2-3x isso)
    const upStems = m.stems.filter((s) => s.dir === 'up');
    const shortest = Math.min(...upStems.map((s) => Math.abs(s.y1 - s.y2)));
    expect(shortest).toBeGreaterThanOrEqual(STEM_LEN - 2);
    expect(shortest).toBeLessThanOrEqual(STEM_LEN + 6);
  });

  it('sem tuplet -> nenhum colchete de tercina', () => {
    expect(engrave(groove).tuplets).toHaveLength(0);
  });
});

describe('engrave - pausa consolidada quando uma voz cala um tempo inteiro', () => {
  it('caixa so no 2 e no 4 -> pausas de semininma nos tempos 1 e 3', () => {
    const m = engrave(
      pat({
        stepsPerBeat: 2,
        bars: 1,
        voices: ['snare'],
        hits: { snare: [2, 6] },
      }),
    );
    const quarters = m.rests.filter((r) => r.kind === 'quarter');
    expect(quarters.length).toBeGreaterThanOrEqual(2);
  });
});

describe('engrave - tuplet (tercina / shuffle)', () => {
  const tercina = pat({
    stepsPerBeat: 3,
    tuplet: true,
    bars: 1,
    voices: ['hihat', 'snare', 'kick'],
    hits: {
      hihat: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11],
      snare: [3, 9],
      kick: [0, 6],
    },
  });

  it('total conta 3 steps por tempo', () => {
    expect(engrave(tercina).total).toBe(12);
  });

  it('nunca gera barra de ligadura secundaria (sem semicolcheia em tercina)', () => {
    const m = engrave(tercina);
    expect(m.beams.length).toBeGreaterThan(0);
    expect(m.beams.every((b) => !b.secondary)).toBe(true);
  });

  it('tres colcheias de tercina num tempo -> uma barra primaria', () => {
    const m = engrave(tercina);
    expect(m.beams.filter((b) => !b.secondary).length).toBe(4);
  });

  it('um colchete "3" por tempo com notas de mao', () => {
    const m = engrave(tercina);
    expect(m.tuplets).toHaveLength(4); // 4 tempos, chimbal em todos
    expect(m.tuplets.every((t) => t.text === '3')).toBe(true);
    expect(m.tuplets.every((t) => t.x2 > t.x1)).toBe(true);
  });
});

describe('engrave - rudimento (voz unica + sticking + acentos)', () => {
  const paradiddle = pat({
    stepsPerBeat: 4,
    bars: 1,
    voices: ['snare'],
    hits: { snare: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15] },
    accents: { snare: [0, 4, 8, 12] },
    sticking: ['R', 'L', 'R', 'R', 'L', 'R', 'L', 'L'],
  });

  it('uma letra de sticking por hit, ciclando a sequencia', () => {
    const m = engrave(paradiddle);
    expect(m.sticking).toHaveLength(16);
    expect(m.sticking.slice(0, 8).map((s) => s.label)).toEqual([
      'R', 'L', 'R', 'R', 'L', 'R', 'L', 'L',
    ]);
    expect(m.sticking.slice(8, 16).map((s) => s.label)).toEqual([
      'R', 'L', 'R', 'R', 'L', 'R', 'L', 'L',
    ]);
  });

  it('acentos: 4 marcas, e as cabecas correspondentes vem com accent=true', () => {
    const m = engrave(paradiddle);
    expect(m.accents).toHaveLength(4);
    expect(m.heads.filter((h) => h.accent)).toHaveLength(4);
    expect(m.heads.filter((h) => h.accent).map((h) => h.step)).toEqual([0, 4, 8, 12]);
  });

  it('sticking so sai em padrao de voz unica', () => {
    const m = engrave(
      pat({
        voices: ['snare', 'kick'],
        hits: { snare: [0], kick: [0] },
        sticking: ['R', 'L'],
      }),
    );
    expect(m.sticking).toHaveLength(0);
  });
});

describe('engrave - crash gera linha suplementar; kick haste pra baixo', () => {
  it('crash (pos 11) -> linha suplementar em pos 10', () => {
    const m = engrave(
      pat({
        stepsPerBeat: 4,
        bars: 1,
        voices: ['crash', 'snare', 'kick'],
        hits: { crash: [0], snare: [4], kick: [0] },
      }),
    );
    expect(m.ledgerLines.length).toBeGreaterThanOrEqual(1);
    expect(m.heads.some((h) => h.voice === 'crash' && h.headType === 'x')).toBe(true);
  });

  it('kick -> todas as hastes pra baixo; vozes de mao -> pra cima', () => {
    const m = engrave(
      pat({
        stepsPerBeat: 4,
        bars: 1,
        voices: ['hihat', 'kick'],
        hits: { hihat: [0, 4, 8, 12], kick: [0, 8] },
      }),
    );
    const kickBeat = 8; // step 8 -> beat 2
    // hastes da voz de pe apontam pra baixo
    expect(m.stems.filter((s) => s.dir === 'down').length).toBeGreaterThan(0);
    expect(m.stems.filter((s) => s.dir === 'up').length).toBeGreaterThan(0);
    // sanity: ha uma cabeca de kick no step 8
    expect(m.heads.some((h) => h.voice === 'kick' && h.step === kickBeat)).toBe(true);
  });
});

describe('engrave - dimensoes e playhead', () => {
  it('viewBox e totalWidth coerentes com padX/colW/total', () => {
    const m = engrave(pat({ stepsPerBeat: 4, bars: 2, voices: ['snare'], hits: { snare: [0] } }));
    expect(m.total).toBe(32);
    expect(m.totalWidth).toBe(m.total * m.colW);
    expect(m.viewBox).toBe(`0 0 ${m.width} ${m.height}`);
    expect(m.padX).toBe(50);
  });

  it('dot-grid tem uma linha por voz presente, na ordem canonica', () => {
    const m = engrave(pat({ voices: ['kick', 'hihat', 'snare'], hits: {} }));
    expect(m.rows.map((r) => r.voice)).toEqual(['hihat', 'snare', 'kick']);
    expect(m.rows.every((r) => r.cells.length === m.total)).toBe(true);
  });
});

describe('toggleHit', () => {
  const base = pat({
    stepsPerBeat: 4,
    bars: 1,
    voices: ['snare'],
    hits: { snare: [0, 4] },
  });

  it('liga um step ausente, mantendo ordenado, sem mutar o original', () => {
    const next = toggleHit(base, 'snare', 2);
    expect(next.hits.snare).toEqual([0, 2, 4]);
    expect(base.hits.snare).toEqual([0, 4]);
    expect(next).not.toBe(base);
  });

  it('desliga um step presente', () => {
    expect(toggleHit(base, 'snare', 4).hits.snare).toEqual([0]);
  });

  it('cria o array da voz quando ela ainda nao tem hits', () => {
    expect(toggleHit(base, 'kick', 8).hits.kick).toEqual([8]);
  });
});
