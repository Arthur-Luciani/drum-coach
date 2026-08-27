import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { ApiService } from '../../api';
import { KeyboardShortcutsService } from '../../core/keyboard-shortcuts.service';
import { MetronomeService } from '../../core/metronome.service';
import { TrainingPickerOverlayService } from '../../core/training-picker-overlay.service';
import { Exercise } from '../../models';
import { DrumSheetComponent } from '../../shared/drum-sheet';
import { formatClock, formatDurationLabel } from '../../shared/format';
import { KeyCap } from '../../shared/key-cap';
import { COMPASSO_FIXO, SessionStateService } from './session-state.service';

const BPM_MIN = 30;
const BPM_MAX = 300;
const BPM_STEP = 1;
const BPM_STEP_SHIFT = 5;

/** Opcoes de "como se sentiu" da revisao - `feeling` e texto livre no backend, essas
 * pills so preenchem esse texto (nao ha enum travando o valor). */
const SENSACAO_OPCOES = ['Ruim', 'OK', 'Bom', 'Ótimo'] as const;

/**
 * Rota `/session` - Modo Sessao completo. Dois caminhos, conforme `trainingId` estar ou
 * nao na query string:
 *
 * - GUIADO (`trainingId` presente, Fase 3e-1/3e-2): count-in -> exercicio rodando com
 *   timer automatico (aviso visual+sonoro ao cruzar `targetDurationSeconds`, sem forcar
 *   avanco) -> ciclando por todos os exercicios do treino escolhido -> revisao
 *   pre-preenchida (resumo, exercicios, sensacao, notas) -> `POST /api/executions`.
 * - LIVRE (`trainingId` ausente, Fase 3e-3, artboard `SessionModeFree` do mockup
 *   Woodshed): sem count-in nem exercicios - direto pro cronometro + metronomo manual +
 *   nota rapida. `Esc` encerra e vai pra revisao (ao contrario do fluxo guiado, onde
 *   `Esc` durante a execucao abandona sem salvar - na sessao livre nao ha "ultimo
 *   exercicio" pra sinalizar o fim, entao `Esc` e essa sinalizacao). Revisao sem a secao
 *   de exercicios (nao ha nenhum), salva com `trainingId: null` (ver
 *   docs/adr/0010-execucao-sem-treino-sessao-livre.md - `execution.training_id` e
 *   nullable desde a Fase 3e-3, decisao confirmada com o usuario).
 *
 * Orquestra tres servicos: `SessionStateService` (escopado a este componente via
 * `providers`, maquina de estados + acumulo dos dados da sessao), `MetronomeService`
 * (singleton, motor de audio) e `KeyboardShortcutsService` (singleton, pilha de escopos
 * de teclado - este componente empilha um scope com `blockFallthrough: true`, tela
 * imersiva como os outros overlays).
 *
 * Decisao de design (ver tambem `SessionStateService.buildExecutionRequest`): o servico
 * de estado so monta o `CreateExecutionRequest` (dado puro, sem HTTP); esta pagina e
 * quem chama `ApiService.createExecution` de fato e decide o que fazer com o resultado
 * (navegar pro Dashboard, ou mostrar erro sem perder o que o usuario preencheu). A tela
 * de revisao fica dentro deste mesmo componente/arquivo (`session.html`), sem um
 * `session-review.ts` separado - o fluxo inteiro do Modo Sessao e pequeno o bastante pra
 * nao justificar a divisao ainda; se crescer, extrair fica facil (o estado ja vive todo
 * no `SessionStateService`, nao neste componente).
 */
@Component({
  selector: 'app-session',
  imports: [KeyCap, DrumSheetComponent],
  providers: [SessionStateService],
  templateUrl: './session.html',
  styleUrl: './session.css',
})
export class SessionPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly shortcuts = inject(KeyboardShortcutsService);
  private readonly api = inject(ApiService);
  private readonly trainingPickerOverlay = inject(TrainingPickerOverlayService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly sessionState = inject(SessionStateService);
  protected readonly metronome = inject(MetronomeService);
  protected readonly formatClock = formatClock;
  protected readonly sensacaoOpcoes = SENSACAO_OPCOES;

  protected readonly trainingId = signal<number | null>(null);
  protected readonly trainingName = signal<string | null>(null);
  protected readonly loading = signal(false);
  /** Mensagem curta pra estados sem sessao rodando: treino sem exercicio nenhum, ou
   * falha ao carregar - ambos so mostram a mensagem + `Escape` pra voltar. */
  protected readonly problemMessage = signal<string | null>(null);

  protected readonly salvando = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly exercicioAtual = computed<Exercise | null>(
    () => this.sessionState.exercicios()[this.sessionState.indiceAtual()] ?? null,
  );

  protected readonly totalExercicios = computed(() => this.sessionState.exercicios().length);

  /** Posicao (0..1) e step do playhead da drum sheet no exercicio `TOCA_JUNTO` atual -
   * alimentados por um loop `requestAnimationFrame` (ver `startPlayheadLoop`) que le o
   * relogio continuo do metronomo (`temposDecorridosNoLoop`). `0` / `null` quando nao ha
   * padrao rodando. */
  protected readonly playheadPos = signal(0);
  protected readonly playheadStep = signal<number | null>(null);

  /** Handle do rAF do playhead - `null` quando parado. */
  private playheadRaf: number | null = null;

  /** `true` quando o exercicio atual e um `TOCA_JUNTO` com padrao tocavel - decide entre
   * a drum sheet (com playhead) e o bloco de texto/transcricao no template. */
  protected readonly exercicioTemPattern = computed(() => {
    const ex = this.exercicioAtual();
    return ex?.kind === 'TOCA_JUNTO' && ex.pattern != null;
  });

  /** Rotulo de duracao total da sessao pra revisao (ex: "38 min", "45s") - `logs()`
   * ainda nao inclui o exercicio atual (so os ja confirmados), o que e exatamente o
   * total certo dentro de `'reviewing'` (todos ja foram confirmados nesse ponto). */
  protected readonly totalSegundosLabel = computed(
    () => formatDurationLabel(this.sessionState.totalSegundos()) ?? '0 min',
  );

  /** Um item por tempo do compasso (fixo em 4, ver `COMPASSO_FIXO`) - alimenta os
   * pontinhos de pulso do metronomo, reaproveitados tanto no count-in quanto no
   * exercicio rodando (`tocando`/`tempoAtual` cobrem os dois, ver ajuste em
   * `MetronomeService.tocarCountIn`). */
  protected readonly beatDots = Array.from({ length: COMPASSO_FIXO }, (_, i) => i);

  /** Textarea de "nota rapida" da sessao livre - `Enter` foca nela (ver
   * `onEnterLivre`/artboard `SessionModeFree`), sem depender de clique do mouse. */
  protected readonly quickNoteEl = viewChild<ElementRef<HTMLTextAreaElement>>('quickNote');

  constructor() {
    const raw = this.route.snapshot.queryParamMap.get('trainingId');
    const parsed = raw != null ? Number(raw) : NaN;
    const id = Number.isFinite(parsed) ? parsed : null;
    this.trainingId.set(id);

    // Cobre tanto a saida via `Escape` (que ja chama `sessionState.encerrar()`) quanto o
    // usuario navegando pra outro lugar no meio de uma sessao sem passar por `Escape`.
    this.destroyRef.onDestroy(() => this.sessionState.destruir());
    this.destroyRef.onDestroy(() => this.stopPlayheadLoop());

    // Liga/desliga o rAF do playhead conforme o estado da sessao e o tipo do exercicio -
    // so roda enquanto um `TOCA_JUNTO` com padrao esta em `'running'`/`'timeUp'`.
    effect(() => {
      const estado = this.sessionState.estado();
      const rodando = estado === 'running' || estado === 'timeUp';
      if (rodando && this.exercicioTemPattern()) {
        this.startPlayheadLoop();
      } else {
        this.stopPlayheadLoop();
      }
    });

    if (id != null) {
      this.resolveTrainingName(id);
      this.loadExercisesAndStart(id);
    } else {
      this.sessionState.iniciarLivre();
      this.registerFreeSessionShortcuts();
    }
  }

  protected sair(): void {
    this.sessionState.encerrar();
    void this.router.navigate(['/dashboard']);
  }

  protected adjustBpm(delta: number): void {
    this.metronome.bpm.update((v) => Math.min(BPM_MAX, Math.max(BPM_MIN, Math.round(v + delta))));
  }

  /** Inicia o loop `requestAnimationFrame` que reposiciona o playhead da drum sheet a
   * cada frame, lendo o relogio continuo do metronomo. Idempotente. */
  private startPlayheadLoop(): void {
    if (this.playheadRaf !== null || typeof requestAnimationFrame !== 'function') {
      return;
    }
    const frame = (): void => {
      this.atualizarPlayhead();
      this.playheadRaf = requestAnimationFrame(frame);
    };
    this.playheadRaf = requestAnimationFrame(frame);
  }

  /** Para o loop do playhead e zera a posicao - chamado ao sair de `'running'`/`'timeUp'`
   * e no destroy. */
  private stopPlayheadLoop(): void {
    if (this.playheadRaf !== null) {
      cancelAnimationFrame(this.playheadRaf);
      this.playheadRaf = null;
    }
    this.playheadPos.set(0);
    this.playheadStep.set(null);
  }

  /** Um frame do playhead: `pos = (beats % loopBeats) / loopBeats`, onde `beats` vem de
   * `MetronomeService.temposDecorridosNoLoop()` e `loopBeats = bars * numerador`. Durante
   * o count-in (ou sem relogio) fica em 0. `highlightStep` = step atual no loop. */
  private atualizarPlayhead(): void {
    const pattern = this.exercicioAtual()?.pattern;
    if (!pattern) {
      this.playheadPos.set(0);
      this.playheadStep.set(null);
      return;
    }
    const loopBeats = Math.max(1, Math.floor(pattern.bars) * Math.floor(pattern.timeSignature[0]));
    const beats =
      this.sessionState.estado() === 'countIn' ? 0 : this.metronome.temposDecorridosNoLoop();
    const pos = beats == null ? 0 : (((beats % loopBeats) + loopBeats) % loopBeats) / loopBeats;
    this.playheadPos.set(pos);
    const total = loopBeats * Math.max(1, Math.floor(pattern.stepsPerBeat));
    this.playheadStep.set(total > 0 ? Math.floor(pos * total) % total : null);
  }

  /** Nome do exercicio de um log da revisao - cruza `log.exerciseId` com a lista de
   * exercicios do treino (ja carregada em `sessionState.exercicios()`), sem precisar
   * guardar o nome dentro do proprio log. */
  protected exercicioNome(exerciseId: number): string {
    return this.sessionState.exercicios().find((e) => e.id === exerciseId)?.name ?? `#${exerciseId}`;
  }

  /** Monta o request (via `SessionStateService.buildExecutionRequest`, ver decisao de
   * design no comentario da classe) e salva de verdade. Em sucesso navega pro Dashboard;
   * em erro so mostra a mensagem - o estado continua `'reviewing'` com tudo que o
   * usuario ja preencheu intacto, pra tentar de novo sem perder nada. */
  protected salvar(): void {
    if (this.salvando()) {
      return;
    }
    const id = this.trainingId(); // null = sessao livre (ver ADR-0010)
    const goalId = this.trainingPickerOverlay.goalDetail()?.goal.id ?? null;
    const request = this.sessionState.buildExecutionRequest(id, goalId);

    this.salvando.set(true);
    this.saveError.set(null);
    this.api.createExecution(request).subscribe({
      next: () => {
        this.salvando.set(false);
        void this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.salvando.set(false);
        this.saveError.set('Não foi possível salvar a sessão. Tente novamente. (' + (err?.message ?? err) + ')');
      },
    });
  }

  private loadExercisesAndStart(trainingId: number): void {
    this.loading.set(true);
    this.api.listExercises(trainingId).subscribe({
      next: (exercises) => {
        this.loading.set(false);
        const ordenados = [...exercises].sort((a, b) => a.orderIndex - b.orderIndex);
        if (ordenados.length === 0) {
          this.problemMessage.set('Nenhum exercício cadastrado neste treino ainda.');
          this.registerEscapeOnlyShortcuts();
          return;
        }
        this.sessionState.iniciar(ordenados);
        this.registerRunningShortcuts();
      },
      error: () => {
        this.loading.set(false);
        this.problemMessage.set('Não foi possível carregar os exercícios deste treino.');
        this.registerEscapeOnlyShortcuts();
      },
    });
  }

  /** Scope minimo (so `Escape`) - usado pra sessao livre e pros casos sem sessao rodando
   * (treino vazio, falha de carga). Mesmo `blockFallthrough: true` dos demais overlays. */
  private registerEscapeOnlyShortcuts(): void {
    const unregister = this.shortcuts.register({
      blockFallthrough: true,
      handlers: {
        escape: () => void this.router.navigate(['/dashboard']),
      },
    });
    this.destroyRef.onDestroy(unregister);
  }

  /** Scope completo do Modo Sessao (count-in/exercicio rodando/revisao - um scope so,
   * empilhado uma vez, cobre a sessao inteira). `Enter`/`→` confirmam e avancam durante
   * `'running'`/`'timeUp'` (no-op fora disso, tratado dentro de `confirmarEAvancar`);
   * durante `'reviewing'` `Enter` dispara o save em vez disso. `←` volta pro exercicio
   * anterior (navegacao manual, best-effort, no-op durante a revisao). `1`-`4` sao um
   * atalho opcional pras pills de sensacao na revisao (no-op fora dela). */
  private registerRunningShortcuts(): void {
    const unregister = this.shortcuts.register({
      blockFallthrough: true,
      handlers: {
        escape: () => this.sair(),
        enter: () => this.onEnter(),
        ' ': () => this.sessionState.alternarMetronomo(),
        arrowright: () => this.sessionState.confirmarEAvancar(),
        arrowleft: () => this.sessionState.voltarExercicio(),
        arrowup: (event) => this.adjustBpm(event.shiftKey ? BPM_STEP_SHIFT : BPM_STEP),
        arrowdown: (event) => this.adjustBpm(event.shiftKey ? -BPM_STEP_SHIFT : -BPM_STEP),
        '1': () => this.selecionarSensacaoAtalho(0),
        '2': () => this.selecionarSensacaoAtalho(1),
        '3': () => this.selecionarSensacaoAtalho(2),
        '4': () => this.selecionarSensacaoAtalho(3),
      },
    });
    this.destroyRef.onDestroy(unregister);
  }

  /** `Enter` faz coisas diferentes dependendo do estado: confirma/avanca o exercicio
   * normalmente, ou dispara o save quando ja esta na revisao. */
  private onEnter(): void {
    if (this.sessionState.estado() === 'reviewing') {
      this.salvar();
    } else {
      this.sessionState.confirmarEAvancar();
    }
  }

  /** Scope da sessao livre (Fase 3e-3) - `Espaco`/setas de BPM iguais ao fluxo guiado
   * (`registerRunningShortcuts`), mas sem `←`/`→` (nao ha exercicios pra navegar) e com
   * `Enter`/`Esc` reinterpretados: `Enter` foca a nota rapida em vez de confirmar um
   * exercicio (nao ha nenhum), `Esc` encerra a sessao e vai pra revisao em vez de
   * abandonar (ver doc da classe / `SessionStateService.finalizarLivre`). */
  private registerFreeSessionShortcuts(): void {
    const unregister = this.shortcuts.register({
      blockFallthrough: true,
      handlers: {
        escape: () => this.onEscapeLivre(),
        enter: () => this.onEnterLivre(),
        ' ': () => this.sessionState.alternarMetronomo(),
        arrowup: (event) => this.adjustBpm(event.shiftKey ? BPM_STEP_SHIFT : BPM_STEP),
        arrowdown: (event) => this.adjustBpm(event.shiftKey ? -BPM_STEP_SHIFT : -BPM_STEP),
        '1': () => this.selecionarSensacaoAtalho(0),
        '2': () => this.selecionarSensacaoAtalho(1),
        '3': () => this.selecionarSensacaoAtalho(2),
        '4': () => this.selecionarSensacaoAtalho(3),
      },
    });
    this.destroyRef.onDestroy(unregister);
  }

  /** `Enter` na sessao livre: durante a revisao dispara o save (igual ao fluxo guiado);
   * enquanto ainda esta rodando, foca a nota rapida ("nova nota" no mockup) - o usuario
   * so precisa tocar o teclado quando de fato for escrever algo. */
  private onEnterLivre(): void {
    if (this.sessionState.estado() === 'reviewing') {
      this.salvar();
    } else {
      this.quickNoteEl()?.nativeElement.focus();
    }
  }

  /** `Esc` na sessao livre: durante a revisao abandona sem salvar, igual ao fluxo guiado
   * (`sair()`). Enquanto ainda esta rodando, NAO abandona - encerra a sessao e vai pra
   * revisao (`finalizarLivre`), porque a sessao livre nao tem "ultimo exercicio" pra
   * sinalizar naturalmente o fim. */
  private onEscapeLivre(): void {
    if (this.sessionState.estado() === 'reviewing') {
      this.sair();
    } else {
      this.sessionState.finalizarLivre();
    }
  }

  /** Atalho numerico (`1`-`4`) pras pills de sensacao - so tem efeito durante
   * `'reviewing'`, no-op em qualquer outro estado (evita `1`-`4` fazerem algo
   * inesperado enquanto o exercicio ainda esta rodando). */
  private selecionarSensacaoAtalho(indice: number): void {
    if (this.sessionState.estado() !== 'reviewing') {
      return;
    }
    this.sessionState.feeling.set(SENSACAO_OPCOES[indice]);
  }

  /**
   * O `TrainingPicker` acabou de navegar pra ca com o `GoalDetail` da meta em foco ainda
   * fresco no `TrainingPickerOverlayService` - reaproveita esse contexto em vez de bater
   * na API de novo. So cai pro fallback (nova chamada) se a tela for aberta direto por
   * URL (refresh, link compartilhado) sem esse contexto disponivel.
   */
  private resolveTrainingName(id: number): void {
    const fromContext = this.trainingPickerOverlay.goalDetail()?.trainings.find((t) => t.trainingId === id);
    if (fromContext) {
      this.trainingName.set(fromContext.name);
      return;
    }

    this.api.listTrainings().subscribe({
      next: (trainings) => {
        this.trainingName.set(trainings.find((t) => t.id === id)?.name ?? `#${id}`);
      },
      error: () => {
        this.trainingName.set(`#${id}`);
      },
    });
  }
}
