import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { DrumPattern, DrumVoice } from '../models';
import {
  engrave,
  FlagMark,
  NoteHeadMark,
  toggleHit,
  TupletMark,
} from './pattern-engraver';

const round2 = (n: number): number => Math.round(n * 100) / 100;

/**
 * `DrumSheetComponent` (`<app-drum-sheet>`) - renderiza UM SVG com a pauta engravada
 * (clave de percussao, cabeca `x` pros pratos, hastes ↑ maos / ↓ bumbo, ligaduras,
 * pausas), o dot-grid clicavel por cima e um playhead. Componente de renderizacao PURA:
 * nao tem timer nem `ng serve` - a posicao vem de fora. Todo o calculo de notacao vem do
 * `PatternEngraver`.
 *
 * Dois modos de apresentacao das MESMAS marcas do engraver:
 * - PADRAO (`roll=false`): a pauta inteira escala pra caber na largura (`viewBox` =
 *   largura do padrao) e um playhead vertical corre sobre ela (`playheadPos` 0..1). Usado
 *   pelo editor de padrao (`editable=true`) e telas de leitura estatica.
 * - ROLANDO (`roll=true`, Modo Sessao): a janela tem largura FIXA (N compassos, nao
 *   depende de `bars`), a linha "agora" fica parada em `playlineFraction` e a pauta e que
 *   corre sob ela, alimentada por `elapsedBeats` (tempos decorridos no loop continuo). O
 *   loop e repetido lado a lado (tiles) pra nunca "acabar" na tela. Clave/formula/rotulos
 *   de voz somem (sem sentido rolando); as notas em si sao identicas ao modo padrao.
 */
@Component({
  selector: 'app-drum-sheet',
  imports: [NgTemplateOutlet],
  templateUrl: './drum-sheet.html',
  styleUrl: './drum-sheet.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DrumSheetComponent {
  /** Documento tocavel a engravar. */
  readonly pattern = input.required<DrumPattern>();
  /** Modo padrao: posicao do playhead no loop, 0..1. */
  readonly playheadPos = input<number>(0);
  /** Step a destacar (cabecas + dots acendem com `.hit`). `null` = nada aceso. No modo
   * `roll` e ignorado - o step aceso e derivado de `elapsedBeats` (a nota que cruza a
   * linha "agora"). */
  readonly highlightStep = input<number | null>(null);
  /** `true` habilita o toggle de celulas do dot-grid. */
  readonly editable = input<boolean>(false);

  /** Liga o modo ROLANDO (ver doc da classe). */
  readonly roll = input<boolean>(false);
  /** Tempos (beats) decorridos no loop continuo - fonte da rolagem. Fracionario, cresce
   * indefinidamente; o componente faz o modulo pelo tamanho do loop. */
  readonly elapsedBeats = input<number>(0);
  /** Quantos compassos a janela mostra no modo `roll`. */
  readonly windowBars = input<number>(2);
  /** Posicao (0..1) da linha "agora" dentro da janela. 0.25 = sobra 3/4 da janela pra
   * ler o que vem chegando pela direita. */
  readonly playlineFraction = input<number>(0.25);

  /** Novo pattern (imutavel) apos togglar uma celula - so emitido quando `editable`. */
  readonly patternChange = output<DrumPattern>();
  /** Celula togglada (voz + step) - so emitido quando `editable`. */
  readonly stepToggle = output<{ voice: DrumVoice; step: number }>();

  /** Modelo de layout do engraver - recomputado so quando `pattern`/`editable` mudam. */
  protected readonly model = computed(() => engrave(this.pattern(), { editable: this.editable() }));

  /** x do playhead (modo padrao): `padX + clamp(pos,0,1) * totalWidth`. */
  protected readonly playheadX = computed(() => {
    const m = this.model();
    const pos = Math.min(1, Math.max(0, this.playheadPos()));
    return m.padX + pos * m.totalWidth;
  });

  // --- modo ROLANDO --------------------------------------------------------

  /** Unidades de engraver por tempo (beat) = stepsPerBeat * colW. */
  private readonly beatUnit = computed(() => {
    const m = this.model();
    return m.stepsPerBeat * m.colW;
  });

  /** Largura FIXA da janela (unid. de engraver): `padX` + N compassos. Nao depende de
   * `bars`, entao a densidade das notas na tela e a mesma em qualquer exercicio. */
  protected readonly rollWidth = computed(() => {
    const m = this.model();
    return m.padX + this.windowBars() * m.timeSignature[0] * this.beatUnit();
  });

  protected readonly rollViewBox = computed(
    () => `0 0 ${round2(this.rollWidth())} ${this.model().height}`,
  );

  /** x fixo (unid. de engraver) da linha "agora". */
  protected readonly playlineXRoll = computed(() =>
    round2(this.playlineFraction() * this.rollWidth()),
  );

  /** Tempo atual dentro do loop, em [0, loopBeats). */
  private readonly elapsedInLoop = computed(() => {
    const lb = Math.max(1, this.model().beats);
    return (((this.elapsedBeats() % lb) + lb) % lb);
  });

  /** translateX (unid. de engraver) que poe o tempo atual sob a linha "agora". */
  private readonly rollShift = computed(
    () => this.playlineXRoll() - (this.model().padX + this.elapsedInLoop() * this.beatUnit()),
  );

  /** Offset de translacao de cada copia do loop - o bastante pra cobrir a janela + uma
   * folga de cada lado, pra pauta nunca "acabar" na tela. */
  protected readonly rollTiles = computed(() => {
    const span = this.model().totalWidth;
    const copies = Math.ceil(this.rollWidth() / span) + 2;
    const shift = this.rollShift();
    return Array.from({ length: copies }, (_, i) => round2(shift + (i - 1) * span));
  });

  /** Linhas da pauta redesenhadas na largura inteira da janela (continuas, sem emenda
   * entre tiles). Reusa as MESMAS posicoes y do engraver. */
  protected readonly rollStaffLines = computed(() => {
    const w = round2(this.rollWidth());
    return this.model().staffLines.map((ln) => ({ x1: 0, x2: w, y: ln.y1 }));
  });

  /** Step aceso: so no modo padrao (input `highlightStep`). No modo `roll` a linha "agora"
   * ja marca a posicao - o brilho no dot/cabeca seria redundante e, com o loop repetido em
   * tiles, apareceria em varios lugares ao mesmo tempo. */
  protected readonly effectiveHighlight = computed(() =>
    this.roll() ? null : this.highlightStep(),
  );

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
