import { DrumPattern, DrumVoice } from '../models';

/**
 * `PatternEngraver` - modulo puro (sem DOM, sem Angular) que transforma um
 * `DrumPattern` (documento estrutural, ver ADR-0011) num MODELO DE LAYOUT ja com
 * coordenadas SVG: linhas da pauta, barras de compasso, clave, formula de compasso,
 * grade, dot-grid clicavel e - por tempo/voz - cabecas de nota, hastes, ligaduras
 * (beams), bandeirolas, pausas consolidadas, linhas suplementares, acentos e letras
 * de sticking.
 *
 * A logica de engrave (valor ritmico pelo gap, agrupamento de beam por tempo, pausa
 * consolidada, posicao de cada voz na pauta) e portada fielmente do mockup de
 * validacao da Fase 4. O componente (`DrumSheetComponent`) so desenha o que sai daqui
 * - nao tem regra de notacao propria.
 */

// --- geometria (unidades de usuario SVG) -----------------------------------
export const PADX = 50;
export const COLW = 15;
export const ENDPAD = 8;
/** Distancia entre duas linhas adjacentes da pauta. */
export const LINE = 10;
/** Meia distancia = um passo de posicao na pauta. `Y(pos) = baseY - pos*HALF`. */
export const HALF = LINE / 2;
/** Comprimento de haste alvo. A barra de ligadura de um grupo fica ~esse tanto acima
 * (mao) / abaixo (pe) da nota mais extrema DO GRUPO - engrave real, barra colada nas
 * notas, em vez de todas as hastes irem ate uma linha fixa la no alto. */
export const STEM_LEN = 30;

// --- vozes ----------------------------------------------------------------
export type HeadType = 'x' | 'o';
export type StaffVoiceKind = 'hand' | 'foot';

export interface InstrMeta {
  /** Rotulo curto exibido a esquerda do dot-grid. */
  label: string;
  /** Posicao vertical na pauta (0 = linha de baixo, sobe de meio em meio espaco). */
  pos: number;
  /** Tipo da cabeca de nota. */
  head: HeadType;
  /** Mao (haste pra cima) ou pe (haste pra baixo). */
  voice: StaffVoiceKind;
  /** Posicoes de linha suplementar (ex: crash acima da pauta). */
  ledger?: number[];
}

/** Mapa das 8 vozes do vocabulario -> geometria/semantica na pauta.
 * Portado do `INSTR` do mockup (que so tinha crash/hihat/tom/caixa/bumbo) e
 * estendido pras 8 vozes conforme a Fase 4c: ride 8, hiTom 7, midTom 6, floorTom 3,
 * snare 5, kick 1, hihat 9, crash 11 (ledger em 10). Cabeca `x` pros pratos
 * (crash/ride/hihat), `o` pro resto. `kick` e a unica voz de pe. */
export const INSTR: Record<DrumVoice, InstrMeta> = {
  crash: { label: 'Crash', pos: 11, head: 'x', voice: 'hand', ledger: [10] },
  hihat: { label: 'Chimbal', pos: 9, head: 'x', voice: 'hand' },
  ride: { label: 'Ride', pos: 8, head: 'x', voice: 'hand' },
  hiTom: { label: 'Tom 1', pos: 7, head: 'o', voice: 'hand' },
  midTom: { label: 'Tom 2', pos: 6, head: 'o', voice: 'hand' },
  snare: { label: 'Caixa', pos: 5, head: 'o', voice: 'hand' },
  floorTom: { label: 'Surdo', pos: 3, head: 'o', voice: 'hand' },
  kick: { label: 'Bumbo', pos: 1, head: 'o', voice: 'foot' },
};

/** Ordem de empilhamento (topo -> base) das linhas do dot-grid. */
export const ROW_ORDER: DrumVoice[] = [
  'crash',
  'hihat',
  'ride',
  'hiTom',
  'midTom',
  'snare',
  'floorTom',
  'kick',
];

// --- valor ritmico -------------------------------------------------------

/** Classificacao ritmica de uma nota: quantos slots ela ocupa (`span`), quantas
 * barras de ligadura (`beams`) e quantas bandeirolas (`flags`) ela pede. */
export interface RhythmValue {
  span: number;
  beams: number;
  flags: number;
}

/** Valor ritmico a partir do gap (em slots de subdivisao) ate a proxima nota.
 * Porta `valueOf` do mockup. */
export function valueOf(gap: number, sub: number, tuplet: boolean): RhythmValue {
  if (tuplet) {
    if (gap >= 2) {
      return { span: 2, beams: 0, flags: 0 };
    }
    return { span: 1, beams: 1, flags: 1 };
  }
  if (gap >= sub) {
    return { span: sub, beams: 0, flags: 0 };
  }
  const eSpan = sub >= 4 ? 2 : 1;
  if (gap >= eSpan) {
    return { span: eSpan, beams: 1, flags: 1 };
  }
  return { span: 1, beams: 2, flags: 2 };
}

/** Alias publico de `valueOf` - "a figura ritmica de um gap". Util pro componente/
 * testes sem reimportar a semantica de `valueOf`. */
export const figureOf = valueOf;

export interface BeatNote {
  slot: number;
  v: RhythmValue;
  span: number;
}
export type RestKind = 'quarter' | 'eighth' | 'sixteenth';
export interface BeatRest {
  slot: number;
  span: number;
  kind: RestKind;
}
export interface BeatLayout {
  notes: BeatNote[];
  rests: BeatRest[];
}

/** Layout de UMA voz (mao ou pe) em UM tempo: notas + pausas consolidadas.
 * Porta `layoutBeat` do mockup. `onsetSlots` sao os slots (0..sub-1) com ataque. */
export function layoutBeat(onsetSlots: number[], sub: number, tuplet: boolean): BeatLayout {
  const notes: BeatNote[] = [];
  const rests: BeatRest[] = [];
  let cursor = 0;
  for (let i = 0; i < onsetSlots.length; i++) {
    const s = onsetSlots[i];
    if (s > cursor) {
      pushRests(rests, cursor, s, sub, tuplet);
    }
    const next = i + 1 < onsetSlots.length ? onsetSlots[i + 1] : sub;
    const gap = next - s;
    const v = valueOf(gap, sub, tuplet);
    const span = Math.min(v.span, gap);
    notes.push({ slot: s, v, span });
    cursor = s + span;
  }
  if (cursor < sub) {
    pushRests(rests, cursor, sub, sub, tuplet);
  }
  return { notes, rests };
}

/** Consolida pausas no intervalo [a, b) de slots. Porta `pushRests` do mockup:
 * tempo inteiro em silencio vira uma pausa de semininma; senao agrupa em colcheias
 * quando alinhado, semicolcheias no resto (tercina = tudo colcheia). */
export function pushRests(
  arr: BeatRest[],
  a: number,
  b: number,
  sub: number,
  tuplet: boolean,
): void {
  if (a >= b) {
    return;
  }
  if (a === 0 && b === sub) {
    arr.push({ slot: 0, span: sub, kind: 'quarter' });
    return;
  }
  if (tuplet) {
    for (let k = a; k < b; k++) {
      arr.push({ slot: k, span: 1, kind: 'eighth' });
    }
    return;
  }
  const chunk = sub >= 4 ? 2 : 1;
  let j = a;
  while (j < b) {
    if (j % chunk === 0 && j + chunk <= b) {
      arr.push({ slot: j, span: chunk, kind: 'eighth' });
      j += chunk;
    } else {
      arr.push({ slot: j, span: 1, kind: 'sixteenth' });
      j++;
    }
  }
}

// --- modelo de layout ---------------------------------------------------

export interface Line {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}
export interface BarLine {
  x: number;
  y1: number;
  y2: number;
  end: boolean;
}
export interface GridLine {
  x: number;
  y1: number;
  y2: number;
  kind: 'sub' | 'beat';
}
export interface DotCell {
  step: number;
  x: number;
  y: number;
  on: boolean;
}
export interface DotRow {
  voice: DrumVoice;
  label: string;
  labelX: number;
  labelY: number;
  y: number;
  cells: DotCell[];
}
export interface NoteHeadMark {
  x: number;
  y: number;
  headType: HeadType;
  voice: DrumVoice;
  step: number;
  accent: boolean;
}
export interface StemMark {
  x: number;
  y1: number;
  y2: number;
  dir: 'up' | 'down';
}
export interface BeamMark {
  x1: number;
  x2: number;
  y: number;
  /** `false` = barra primaria (colcheia), `true` = barra secundaria (semicolcheia). */
  secondary: boolean;
}
export interface FlagMark {
  x: number;
  y: number;
  dir: 'up' | 'down';
}
export interface RestMark {
  x: number;
  y: number;
  kind: RestKind;
}
export interface AccentMark {
  x: number;
  y: number;
}
export interface StickMark {
  x: number;
  y: number;
  label: 'R' | 'L';
}
export interface TupletMark {
  x1: number;
  x2: number;
  y: number;
  text: string;
}
export interface ClefMark {
  x1: number;
  x2: number;
  y: number;
  height: number;
  barWidth: number;
}
export interface TimeSigMark {
  x: number;
  topText: string;
  bottomText: string;
  topY: number;
  bottomY: number;
  fontSize: number;
}

/** Modelo de layout completo devolvido por `engrave`. Tudo ja em coordenadas SVG. */
export interface EngravedStaff {
  width: number;
  height: number;
  viewBox: string;
  /** x da primeira coluna (inicio do loop) - origem do playhead. */
  padX: number;
  colW: number;
  /** `bars * timeSignature[0] * stepsPerBeat`. */
  total: number;
  /** Largura em px que o playhead percorre (`total * colW`). */
  totalWidth: number;
  editable: boolean;
  timeSignature: [number, number];
  stepsPerBeat: number;
  tuplet: boolean;
  bars: number;
  beats: number;

  staffLines: Line[];
  barLines: BarLine[];
  gridLines: GridLine[];
  clef: ClefMark;
  timeSig: TimeSigMark;

  rows: DotRow[];
  heads: NoteHeadMark[];
  stems: StemMark[];
  beams: BeamMark[];
  flags: FlagMark[];
  rests: RestMark[];
  ledgerLines: Line[];
  accents: AccentMark[];
  sticking: StickMark[];
  tuplets: TupletMark[];
}

export interface EngraveOptions {
  editable?: boolean;
}

const round2 = (n: number): number => Math.round(n * 100) / 100;

/** Engrava um `DrumPattern` no modelo de layout. Pura: mesma entrada -> mesma saida,
 * sem tocar em DOM. */
export function engrave(pattern: DrumPattern, opts: EngraveOptions = {}): EngravedStaff {
  const editable = !!opts.editable;
  const sub = Math.max(1, Math.floor(pattern.stepsPerBeat));
  const beatsPerBar = Math.max(1, Math.floor(pattern.timeSignature[0]));
  const bars = Math.max(1, Math.floor(pattern.bars));
  const tuplet = !!pattern.tuplet;
  const beats = bars * beatsPerBar;
  const total = beats * sub;
  const stepsPerBar = beatsPerBar * sub;

  // Vozes na ordem canonica de empilhamento, so as presentes.
  const voices = ROW_ORDER.filter((id) => pattern.voices.includes(id));
  const hits: Partial<Record<DrumVoice, number[]>> = {};
  for (const id of voices) {
    hits[id] = (pattern.hits[id] ?? []).filter((s) => Number.isInteger(s) && s >= 0 && s < total);
  }
  const accents = pattern.accents ?? {};

  const rows = voices;
  const gridH = rows.length * 8 + 6;
  const baseY = gridH + 90;
  const Y = (pos: number): number => baseY - pos * HALF;
  const topLineY = Y(8);
  const botLineY = Y(0);
  // Tetos da ligadura: a barra de mao para ~8 abaixo do dot-grid (deixa folga pro
  // colchete de tercina); a de pe nao desce demais. Fora disso a barra e por grupo
  // (~STEM_LEN da nota mais extrema - ver `engraveVoiceBeat`).
  const beamCeilHand = gridH + 8;
  const beamFloorFoot = botLineY + 44;
  const handRestY = baseY - 22;
  const footRestY = baseY + 6;
  const stickY = baseY + 20;
  const width = PADX + total * COLW + ENDPAD;
  const height = baseY + 44;
  const xAt = (step: number): number => PADX + step * COLW + COLW / 2;
  const xBound = (i: number): number => PADX + i * COLW;

  const model: EngravedStaff = {
    width: round2(width),
    height: round2(height),
    viewBox: `0 0 ${round2(width)} ${round2(height)}`,
    padX: PADX,
    colW: COLW,
    total,
    totalWidth: total * COLW,
    editable,
    timeSignature: [beatsPerBar, Math.max(1, Math.floor(pattern.timeSignature[1]))],
    stepsPerBeat: sub,
    tuplet,
    bars,
    beats,
    staffLines: [],
    barLines: [],
    gridLines: [],
    clef: {
      x1: PADX - 30,
      x2: PADX - 25,
      y: round2(Y(6)),
      height: round2(Y(2) - Y(6)),
      barWidth: 2.6,
    },
    timeSig: {
      x: PADX - 14,
      topText: String(beatsPerBar),
      bottomText: String(Math.max(1, Math.floor(pattern.timeSignature[1]))),
      topY: round2(Y(6) + 1),
      bottomY: round2(Y(2) - 1),
      fontSize: 14,
    },
    rows: [],
    heads: [],
    stems: [],
    beams: [],
    flags: [],
    rests: [],
    ledgerLines: [],
    accents: [],
    sticking: [],
    tuplets: [],
  };

  // grade de subdivisao + tempo (pula onde cai barra de compasso)
  for (let i = 0; i <= total; i++) {
    if (i % stepsPerBar === 0) {
      continue;
    }
    const kind: 'sub' | 'beat' = i % sub === 0 ? 'beat' : 'sub';
    model.gridLines.push({ x: round2(xBound(i)), y1: 4, y2: round2(botLineY + 6), kind });
  }

  // dot-grid
  rows.forEach((id, r) => {
    const ry = 8 + r * 8;
    const on = hits[id] ?? [];
    const cells: DotCell[] = [];
    for (let c = 0; c < total; c++) {
      cells.push({ step: c, x: round2(xAt(c)), y: ry, on: on.includes(c) });
    }
    model.rows.push({
      voice: id,
      label: INSTR[id].label,
      labelX: PADX - 8,
      labelY: ry + 3,
      y: ry,
      cells,
    });
  });

  // linhas da pauta
  for (let l = 0; l < 5; l++) {
    const ly = topLineY + l * LINE;
    model.staffLines.push({
      x1: PADX - 2,
      y1: round2(ly),
      x2: round2(width - ENDPAD),
      y2: round2(ly),
    });
  }

  // barras de compasso
  for (let b = 0; b <= bars; b++) {
    const bx = xBound(b * stepsPerBar);
    model.barLines.push({
      x: round2(bx),
      y1: round2(topLineY),
      y2: round2(botLineY),
      end: b === bars,
    });
  }

  // notas, tempo a tempo: voz de mao (haste pra cima) + voz de pe (haste pra baixo)
  const handIds = voices.filter((id) => INSTR[id].voice === 'hand');
  const footIds = voices.filter((id) => INSTR[id].voice === 'foot');

  for (let beat = 0; beat < beats; beat++) {
    engraveVoiceBeat(model, {
      hits,
      accents,
      beat,
      ids: handIds,
      sub,
      tuplet,
      dir: 'up',
      beamClamp: beamCeilHand,
      restY: handRestY,
      Y,
      xAt,
    });
    engraveVoiceBeat(model, {
      hits,
      accents,
      beat,
      ids: footIds,
      sub,
      tuplet,
      dir: 'down',
      beamClamp: beamFloorFoot,
      restY: footRestY,
      Y,
      xAt,
    });
  }

  // sticking + acentos de rudimento (padrao de voz unica com `sticking`)
  if (pattern.sticking && pattern.sticking.length && voices.length === 1) {
    const only = voices[0];
    const seq = pattern.sticking;
    const sortedHits = [...(hits[only] ?? [])].sort((p, q) => p - q);
    sortedHits.forEach((step, idx) => {
      model.sticking.push({
        x: round2(xAt(step)),
        y: round2(stickY),
        label: seq[idx % seq.length],
      });
    });
  }

  return model;
}

interface VoiceBeatCtx {
  hits: Partial<Record<DrumVoice, number[]>>;
  accents: Partial<Record<DrumVoice, number[]>>;
  beat: number;
  ids: DrumVoice[];
  sub: number;
  tuplet: boolean;
  dir: 'up' | 'down';
  /** Teto (mao) / piso (pe) da barra de ligadura - a barra e por grupo, mas nao passa disto. */
  beamClamp: number;
  restY: number;
  Y: (pos: number) => number;
  xAt: (step: number) => number;
}

/** Engrava uma voz (mao OU pe) em um tempo: pausas consolidadas, cabecas, hastes,
 * ligaduras/bandeirolas. Porta `renderVoiceBeat` do mockup, escrevendo no `model`
 * em vez de concatenar SVG. */
function engraveVoiceBeat(model: EngravedStaff, ctx: VoiceBeatCtx): void {
  const { hits, accents, beat, ids, sub, tuplet, dir, beamClamp, restY, Y, xAt } = ctx;
  if (!ids.length) {
    return;
  }
  const down = dir === 'down';
  const slotInstr: Record<number, DrumVoice[]> = {};
  const onsetSlots: number[] = [];
  for (let k = 0; k < sub; k++) {
    const abs = beat * sub + k;
    const list: DrumVoice[] = [];
    for (const id of ids) {
      if ((hits[id] ?? []).includes(abs)) {
        list.push(id);
      }
    }
    if (list.length) {
      slotInstr[k] = list;
      onsetSlots.push(k);
    }
  }
  const lay = layoutBeat(onsetSlots, sub, tuplet);

  // pausas
  for (const rst of lay.rests) {
    const rcx = xAt(beat * sub + rst.slot) + ((rst.span - 1) * COLW) / 2;
    model.rests.push({ x: round2(rcx), y: round2(restY), kind: rst.kind });
  }

  if (!lay.notes.length) {
    return;
  }

  const stemDX = down ? -4 : 4;
  interface Anchor {
    x: number;
    tipY: number;
    v: RhythmValue;
  }
  const stems: Anchor[] = [];
  const allHeadYs: number[] = [];
  for (const nt of lay.notes) {
    const abs = beat * sub + nt.slot;
    const nx = xAt(abs);
    const instrs = slotInstr[nt.slot];
    let anchor = down ? Number.POSITIVE_INFINITY : Number.NEGATIVE_INFINITY;
    for (const id of instrs) {
      const meta = INSTR[id];
      const ny = Y(meta.pos);
      allHeadYs.push(ny);
      const isAccent = (accents[id] ?? []).includes(abs);
      model.heads.push({
        x: round2(nx),
        y: round2(ny),
        headType: meta.head,
        voice: id,
        step: abs,
        accent: isAccent,
      });
      if (isAccent) {
        model.accents.push({ x: round2(nx), y: round2(ny - 12) });
      }
      if (meta.ledger) {
        for (const lp of meta.ledger) {
          const ly = Y(lp);
          model.ledgerLines.push({
            x1: round2(nx - 7),
            y1: round2(ly),
            x2: round2(nx + 7),
            y2: round2(ly),
          });
        }
      }
      anchor = down ? Math.min(anchor, ny) : Math.max(anchor, ny);
    }
    stems.push({ x: nx + stemDX, tipY: anchor, v: nt.v });
  }

  // Barra de ligadura POR GRUPO: ~STEM_LEN acima (mao) / abaixo (pe) da nota mais
  // extrema deste tempo, mas presa ao teto/piso (`beamClamp`).
  const beamY = down
    ? Math.min(Math.max(...allHeadYs) + STEM_LEN, beamClamp)
    : Math.max(Math.min(...allHeadYs) - STEM_LEN, beamClamp);

  // hastes: da nota ate a barra do grupo
  for (const st of stems) {
    model.stems.push({
      x: round2(st.x),
      y1: round2(st.tipY),
      y2: round2(beamY),
      dir,
    });
  }

  // colchete de tercina sobre o tempo (na passada de mao, que cobre o shuffle)
  if (tuplet && !down) {
    const firstX = xAt(beat * sub + lay.notes[0].slot);
    const lastX = xAt(beat * sub + lay.notes[lay.notes.length - 1].slot);
    model.tuplets.push({
      x1: round2(firstX - 5),
      x2: round2(lastX + 5),
      y: round2(beamY - 7),
      text: '3',
    });
  }

  // beams / flags
  const beamable = stems.filter((st) => st.v.beams >= 1);
  const beamGap = 3.4 * (down ? -1 : 1);
  if (beamable.length >= 2) {
    model.beams.push({
      x1: round2(beamable[0].x),
      x2: round2(beamable[beamable.length - 1].x),
      y: round2(beamY),
      secondary: false,
    });
    for (let bi = 0; bi < beamable.length; bi++) {
      if (beamable[bi].v.beams >= 2) {
        const nb = beamable[bi + 1];
        let x1 = beamable[bi].x;
        let x2 = nb ? nb.x : beamable[bi].x + 6;
        if (!nb) {
          const pv = beamable[bi - 1];
          if (pv) {
            x1 = beamable[bi].x - 6;
            x2 = beamable[bi].x;
          }
        }
        model.beams.push({
          x1: round2(x1),
          x2: round2(x2),
          y: round2(beamY + beamGap),
          secondary: true,
        });
      }
    }
  } else if (beamable.length === 1) {
    const st1 = beamable[0];
    for (let fi = 0; fi < st1.v.flags; fi++) {
      model.flags.push({
        x: round2(st1.x),
        y: round2(beamY + fi * 5 * (down ? -1 : 1)),
        dir,
      });
    }
  }
}

// --- helpers de edicao -------------------------------------------------

/** Devolve um NOVO `DrumPattern` (imutavel) com o `(voice, step)` ligado/desligado
 * em `hits`. Nao mexe em `voices` - o dot-grid so oferece toggle nas vozes ja
 * presentes. Mantem os steps ordenados. */
export function toggleHit(pattern: DrumPattern, voice: DrumVoice, step: number): DrumPattern {
  const current = pattern.hits[voice] ?? [];
  const has = current.includes(step);
  const nextForVoice = has
    ? current.filter((s) => s !== step)
    : [...current, step].sort((a, b) => a - b);
  return {
    ...pattern,
    hits: { ...pattern.hits, [voice]: nextForVoice },
  };
}
