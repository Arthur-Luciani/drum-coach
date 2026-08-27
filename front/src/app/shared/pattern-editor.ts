import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { DrumPattern, DrumVoice } from '../models';
import { DrumSheetComponent } from './drum-sheet';
import { INSTR } from './pattern-engraver';
import { clonePattern, PATTERN_PRESETS, VOICE_VOCABULARY } from './pattern-presets';

/**
 * `PatternEditorComponent` (`<app-pattern-editor>`) - editor de um `DrumPattern`
 * (documento tocavel de um exercicio `TOCA_JUNTO`, ver ADR-0011). Envolve o
 * `<app-drum-sheet editable>` (que ja faz o toggle celula-a-celula do dot-grid) e
 * acrescenta os controles estruturais que o dot-grid nao cobre: presets, formula de
 * compasso, subdivisao/tercina, numero de compassos e o vocabulario de vozes.
 *
 * Componente CONTROLADO: nao guarda estado proprio - recebe `pattern` e emite
 * `patternChange` com um documento NOVO (imutavel) a cada edicao; o dono (form de
 * criacao/edicao de exercicio) mantem o valor corrente.
 *
 * Fora de escopo nesta fase (ADR-0011 "Fase 4d"): editor dedicado de acento e de
 * sticking - por ora esses so entram via preset (ex: "Paradiddle com acentos"). Mudar
 * a estrutura pra deixar de ter voz unica descarta o `sticking`.
 */
@Component({
  selector: 'app-pattern-editor',
  imports: [DrumSheetComponent],
  templateUrl: './pattern-editor.html',
  styleUrl: './pattern-editor.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PatternEditorComponent {
  readonly pattern = input.required<DrumPattern>();
  readonly patternChange = output<DrumPattern>();

  protected readonly presets = PATTERN_PRESETS;
  protected readonly denominators = [2, 4, 8, 16];

  /** Vocabulario com rotulo e estado de presenca, na ordem canonica de empilhamento. */
  protected readonly voiceOptions = computed(() => {
    const present = new Set(this.pattern().voices);
    return VOICE_VOCABULARY.map((voice) => ({
      voice,
      label: INSTR[voice].label,
      present: present.has(voice),
    }));
  });

  /** `total = bars * numerador * stepsPerBeat` - so pra exibir/testar. */
  protected readonly total = computed(() => {
    const p = this.pattern();
    return this.totalOf(p.bars, p.timeSignature[0], p.stepsPerBeat);
  });

  /** Substitui o pattern inteiro pelo preset escolhido (copia profunda - nunca mexe no
   * objeto compartilhado do array de presets). */
  protected applyPreset(key: string): void {
    const preset = this.presets.find((p) => p.key === key);
    if (preset) {
      this.patternChange.emit(clonePattern(preset.pattern));
    }
  }

  /** Repassa o `patternChange` do dot-grid (toggle de hit) - ja vem imutavel do
   * `<app-drum-sheet>`/`toggleHit`. */
  protected onSheetPattern(next: DrumPattern): void {
    this.patternChange.emit(next);
  }

  protected setNumerator(value: string): void {
    const n = this.parseInt(value, 1, 16);
    const p = this.pattern();
    this.emitStructural({ ...p, timeSignature: [n, p.timeSignature[1]] });
  }

  protected setDenominator(value: string): void {
    const d = this.parseInt(value, 1, 16);
    const p = this.pattern();
    this.emitStructural({ ...p, timeSignature: [p.timeSignature[0], d] });
  }

  protected setStepsPerBeat(value: string): void {
    this.emitStructural({ ...this.pattern(), stepsPerBeat: this.parseInt(value, 1, 8) });
  }

  protected setBars(value: string): void {
    this.emitStructural({ ...this.pattern(), bars: this.parseInt(value, 1, 8) });
  }

  protected toggleTuplet(): void {
    this.emitStructural({ ...this.pattern(), tuplet: !this.pattern().tuplet });
  }

  /** Adiciona/remove uma voz do vocabulario. Ao remover, descarta hits/accents dessa
   * voz; o `sticking` some se deixar de ser padrao de voz unica (via `emitStructural`). */
  protected toggleVoice(voice: DrumVoice): void {
    const p = this.pattern();
    const has = p.voices.includes(voice);
    const voices = has
      ? p.voices.filter((v) => v !== voice)
      : VOICE_VOCABULARY.filter((v) => v === voice || p.voices.includes(v));
    this.emitStructural({ ...p, voices });
  }

  /** Emite um pattern novo depois de um ajuste estrutural, sempre reconciliado:
   * hits/accents fora do novo `total` ou de voz ausente sao descartados; `sticking` so
   * sobrevive com voz unica. */
  private emitStructural(next: DrumPattern): void {
    const total = this.totalOf(next.bars, next.timeSignature[0], next.stepsPerBeat);
    const voiceSet = new Set(next.voices);
    const inRange = (s: number): boolean => Number.isInteger(s) && s >= 0 && s < total;

    const hits: Partial<Record<DrumVoice, number[]>> = {};
    for (const v of next.voices) {
      const kept = (next.hits[v] ?? []).filter(inRange);
      if (kept.length) {
        hits[v] = kept;
      }
    }

    let accents: Partial<Record<DrumVoice, number[]>> | undefined;
    if (next.accents) {
      const acc: Partial<Record<DrumVoice, number[]>> = {};
      for (const v of next.voices) {
        const kept = (next.accents[v] ?? []).filter((s) => inRange(s) && (hits[v] ?? []).includes(s));
        if (kept.length) {
          acc[v] = kept;
        }
      }
      accents = Object.keys(acc).length ? acc : undefined;
    }

    let sticking = next.sticking;
    if (sticking && (next.voices.length !== 1 || !voiceSet.has(next.voices[0]))) {
      sticking = undefined;
    }

    this.patternChange.emit({ ...next, hits, accents, sticking });
  }

  private totalOf(bars: number, numerator: number, stepsPerBeat: number): number {
    return (
      Math.max(1, Math.floor(bars)) *
      Math.max(1, Math.floor(numerator)) *
      Math.max(1, Math.floor(stepsPerBeat))
    );
  }

  private parseInt(value: string, min: number, max: number): number {
    const n = Math.floor(Number(value));
    if (!Number.isFinite(n)) {
      return min;
    }
    return Math.min(max, Math.max(min, n));
  }
}
