import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { DrumPattern, DrumVoice } from '../models';
import {
  engrave,
  FlagMark,
  NoteHeadMark,
  toggleHit,
  TupletMark,
} from './pattern-engraver';

/**
 * `DrumSheetComponent` (`<app-drum-sheet>`) - renderiza UM SVG com a pauta engravada
 * (clave de percussao, cabeca `x` pros pratos, hastes ↑ maos / ↓ bumbo, ligaduras,
 * pausas), o dot-grid clicavel por cima e um playhead vertical. Componente de
 * renderizacao PURA: nao tem timer nem `ng serve` - o playhead vem de fora via
 * `playheadPos`. Todo o calculo de notacao vem do `PatternEngraver`.
 *
 * Reusado (Fase 4d) entre o Modo Sessao (le, `editable=false`) e o editor de padrao
 * (edita, `editable=true`, togglando celulas do dot-grid).
 */
@Component({
  selector: 'app-drum-sheet',
  templateUrl: './drum-sheet.html',
  styleUrl: './drum-sheet.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DrumSheetComponent {
  /** Documento tocavel a engravar. */
  readonly pattern = input.required<DrumPattern>();
  /** Posicao do playhead no loop, 0..1. */
  readonly playheadPos = input<number>(0);
  /** Step a destacar (cabecas + dots acendem com `.hit`). `null` = nada aceso. */
  readonly highlightStep = input<number | null>(null);
  /** `true` habilita o toggle de celulas do dot-grid. */
  readonly editable = input<boolean>(false);

  /** Novo pattern (imutavel) apos togglar uma celula - so emitido quando `editable`. */
  readonly patternChange = output<DrumPattern>();
  /** Celula togglada (voz + step) - so emitido quando `editable`. */
  readonly stepToggle = output<{ voice: DrumVoice; step: number }>();

  /** Modelo de layout do engraver - recomputado so quando `pattern`/`editable` mudam. */
  protected readonly model = computed(() => engrave(this.pattern(), { editable: this.editable() }));

  /** x do playhead: `padX + clamp(pos,0,1) * totalWidth`. Reativo aos inputs. */
  protected readonly playheadX = computed(() => {
    const m = this.model();
    const pos = Math.min(1, Math.max(0, this.playheadPos()));
    return m.padX + pos * m.totalWidth;
  });

  protected readonly ariaLabel = computed(() => {
    const p = this.pattern();
    const [n, d] = p.timeSignature;
    return `Partitura de bateria, compasso ${n}/${d}, ${p.bars} compasso(s), vozes: ${p.voices.join(', ')}`;
  });

  protected onCellClick(voice: DrumVoice, step: number): void {
    if (!this.editable()) {
      return;
    }
    this.stepToggle.emit({ voice, step });
    this.patternChange.emit(toggleHit(this.pattern(), voice, step));
  }

  /** Path da cabeca `x` (dois tracos cruzados), centrada em (h.x, h.y). */
  protected xHeadPath(h: NoteHeadMark): string {
    const { x, y } = h;
    return (
      `M${x - 3.3} ${y - 3.3} L${x + 3.3} ${y + 3.3} ` +
      `M${x - 3.3} ${y + 3.3} L${x + 3.3} ${y - 3.3}`
    );
  }

  /** Path de uma bandeirola (quadratica), a partir do topo/base da haste. */
  protected flagPath(fl: FlagMark): string {
    const sweep = fl.dir === 'down' ? '-9 -4 -6 -14' : '9 4 6 14';
    return `M${fl.x} ${fl.y} q ${sweep}`;
  }

  /** Path do colchete de tercina: `⌐ ... ⌐` com um vao no meio pro numero. */
  protected tupletPath(t: TupletMark): string {
    const mid = (t.x1 + t.x2) / 2;
    return (
      `M${t.x1} ${t.y + 4} L${t.x1} ${t.y} L${mid - 4} ${t.y} ` +
      `M${mid + 4} ${t.y} L${t.x2} ${t.y} L${t.x2} ${t.y + 4}`
    );
  }
}
