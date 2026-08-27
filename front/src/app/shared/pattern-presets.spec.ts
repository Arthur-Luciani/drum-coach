import { describe, expect, it } from 'vitest';

import { DrumPattern } from '../models';
import { emptyPattern, PATTERN_PRESETS } from './pattern-presets';

/** `total = bars * numerador * stepsPerBeat` (ver ADR-0011). */
function totalOf(p: DrumPattern): number {
  return p.bars * p.timeSignature[0] * p.stepsPerBeat;
}

describe('PATTERN_PRESETS', () => {
  it('tem os 3 presets nomeados esperados', () => {
    expect(PATTERN_PRESETS.map((p) => p.key)).toEqual(['groove-4-4', 'paradiddle', 'shuffle']);
    for (const preset of PATTERN_PRESETS) {
      expect(preset.label.length).toBeGreaterThan(0);
    }
  });

  for (const preset of PATTERN_PRESETS) {
    describe(preset.key, () => {
      const p = preset.pattern;
      const total = totalOf(p);

      it('version 1 e aritmetica de total coerente', () => {
        expect(p.version).toBe(1);
        expect(total).toBeGreaterThan(0);
      });

      it('todos os hits caem em [0, total) e as vozes estao no documento', () => {
        for (const [voice, steps] of Object.entries(p.hits)) {
          expect(p.voices).toContain(voice);
          for (const s of steps ?? []) {
            expect(Number.isInteger(s)).toBe(true);
            expect(s).toBeGreaterThanOrEqual(0);
            expect(s).toBeLessThan(total);
          }
        }
      });

      it('accents (se houver) sao subconjunto dos hits da mesma voz', () => {
        for (const [voice, steps] of Object.entries(p.accents ?? {})) {
          const hits = p.hits[voice as keyof typeof p.hits] ?? [];
          for (const s of steps ?? []) {
            expect(hits).toContain(s);
          }
        }
      });

      it('sticking (se houver) so com voz unica e comprimento == total', () => {
        if (p.sticking) {
          expect(p.voices).toHaveLength(1);
          expect(p.sticking).toHaveLength(total);
        }
      });
    });
  }
});

describe('emptyPattern', () => {
  it('e um DrumPattern valido, sem hits, com o kit basico', () => {
    const p = emptyPattern();
    expect(p.version).toBe(1);
    expect(p.voices).toEqual(['hihat', 'snare', 'kick']);
    expect(Object.keys(p.hits)).toHaveLength(0);
    expect(totalOf(p)).toBe(16);
  });

  it('devolve uma instancia nova a cada chamada (nao compartilha estado)', () => {
    const a = emptyPattern();
    const b = emptyPattern();
    expect(a).not.toBe(b);
    a.hits.snare = [0];
    expect(b.hits.snare).toBeUndefined();
  });
});
