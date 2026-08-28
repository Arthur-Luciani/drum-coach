import { Injectable, inject, signal } from '@angular/core';

import { MetronomeService, subdivisaoDoPattern } from '../../core/metronome.service';
import { CreateExecutionRequest, ExecutionExerciseLogRequest, Exercise } from '../../models';

/**
 * Estado do Modo Sessao - os 5 estados canonicos do plano (ver
 * `docs/adr/0008-identidade-e-jornadas-woodshed.md` / plano Woodshed secao 5), todos com
 * logica de verdade a partir da Fase 3e-2: `'timeUp'` sinaliza que o exercicio atual
 * cruzou `targetDurationSeconds` (visual + sonoro, sem forcar avanco - `'running'` e
 * `'timeUp'` se comportam identico pra `confirmarEAvancar`/`voltarExercicio`/
 * `alternarMetronomo`, so mudam o que a view desenha). `'reviewing'` e o fim real da
 * sessao: resumo pre-preenchido (`logs`/`totalSegundos`) + `feeling`/`generalNotes`
 * editaveis, ate o usuario salvar (`SessionPage` chama `ApiService.createExecution` com
 * `buildExecutionRequest`) ou sair sem salvar (`Escape`).
 */
export type SessaoEstado = 'idle' | 'countIn' | 'running' | 'timeUp' | 'reviewing';

/** BPM usado quando o exercicio nao tem `targetBpm` definido - nao ha por que travar a
 * sessao por falta de um numero, 100 e um andamento neutro de aquecimento. */
const BPM_FALLBACK = 100;

/** Compasso do count-in/metronomo do Modo Sessao quando o exercicio nao tem um `pattern`
 * de onde herdar a formula de compasso (`TRANSCRICAO`, ou `TOCA_JUNTO` ainda sem grade).
 * Exercicio com `pattern` usa `pattern.timeSignature[0]`. Exportado so pros testes -
 * `SessionPage` desenha os pontinhos de pulso a partir de `metronome.compasso()`. */
export const COMPASSO_FALLBACK = 4;

/**
 * Maquina de estados do Modo Sessao (ver plano Woodshed, decisao de arquitetura #5).
 * Escopada ao componente `SessionPage` (`providers: [SessionStateService]`, NUNCA
 * `providedIn: 'root'`) - cada entrada na rota `/session` comeca do zero, sem estado
 * vazado de uma sessao anterior.
 *
 * Orquestra o `MetronomeService` singleton: count-in isolado (`tocarCountIn`) seguido do
 * loop continuo (`iniciar`) enquanto o exercicio esta rodando, mais um cronometro
 * (`setInterval` de 1s) contando `segundosDecorridos` do exercicio atual.
 */
@Injectable()
export class SessionStateService {
  private readonly metronome = inject(MetronomeService);

  private readonly estadoSignal = signal<SessaoEstado>('idle');
  readonly estado = this.estadoSignal.asReadonly();

  private readonly exerciciosSignal = signal<Exercise[]>([]);
  readonly exercicios = this.exerciciosSignal.asReadonly();

  private readonly indiceAtualSignal = signal(0);
  readonly indiceAtual = this.indiceAtualSignal.asReadonly();

  private readonly segundosDecorridosSignal = signal(0);
  readonly segundosDecorridos = this.segundosDecorridosSignal.asReadonly();

  /** `true` assim que o aviso sonoro/visual de tempo cumprido ja tocou pro exercicio
   * atual - impede disparar de novo a cada tick do cronometro depois da transicao pra
   * `'timeUp'`. Resetado a cada novo exercicio em `iniciarExercicioAtual`. */
  private readonly avisoTocadoSignal = signal(false);

  /** Um log por exercicio confirmado, na ordem em que foram confirmados - acumulado
   * dentro de `confirmarEAvancar`, vira `CreateExecutionRequest.logs` na revisao final
   * (`buildExecutionRequest`). `notes` fica sempre `null` nesta sub-etapa - nao ha campo
   * de nota por exercicio na UI ainda (opcional no schema, sem problema). */
  private readonly logsSignal = signal<ExecutionExerciseLogRequest[]>([]);
  readonly logs = this.logsSignal.asReadonly();

  /** Soma do `segundosDecorridos` de cada exercicio confirmado - duracao total da
   * sessao, usada no resumo da revisao e em `actualDurationMinutes`. */
  private readonly totalSegundosSignal = signal(0);
  readonly totalSegundos = this.totalSegundosSignal.asReadonly();

  /** "Como se sentiu"/notas gerais da sessao - writable, a tela de revisao liga direto
   * (pills de sensacao chamam `feeling.set(...)`, textarea chama `generalNotes.set(...)`
   * no `(input)`). Sem metodo proprio: um signal simples ja serve pro que a UI precisa. */
  readonly feeling = signal<string | null>(null);
  readonly generalNotes = signal<string | null>(null);

  private cronometroHandle: ReturnType<typeof setInterval> | null = null;

  /** Token de corrida: incrementado toda vez que uma nova execucao de exercicio comeca
   * ou a sessao e encerrada/destruida. `tocarCountIn` e assincrono (pode levar varios
   * segundos) - se o usuario avancar/voltar/sair antes dele resolver, a resolucao antiga
   * chega tarde e nao deve mais iniciar o loop continuo do metronomo por cima do estado
   * atual (senao o metronomo ficaria tocando um BPM errado, ou tocando depois do usuario
   * ja ter saido da tela). */
  private execucaoToken = 0;

  /** Comeca a sessao com a lista de exercicios do treino escolhido (ja ordenada por
   * `orderIndex` por quem chama). Zera o progresso e dispara o count-in do primeiro. */
  iniciar(exercicios: Exercise[]): void {
    this.exerciciosSignal.set(exercicios);
    this.indiceAtualSignal.set(0);
    this.logsSignal.set([]);
    this.totalSegundosSignal.set(0);
    this.feeling.set(null);
    this.generalNotes.set(null);
    this.iniciarExercicioAtual();
  }

  /** Comeca a sessao LIVRE (Fase 3e-3 - sem treino/exercicios escolhidos, so
   * cronometro/metronomo, ver artboard `SessionModeFree` do mockup Woodshed). Direto pro
   * cronometro contando, sem count-in (nao ha bpm/compasso alvo de exercicio nenhum pra
   * contar contra - o usuario liga o metronomo manualmente, `Espaco`, se quiser). */
  iniciarLivre(): void {
    this.exerciciosSignal.set([]);
    this.indiceAtualSignal.set(0);
    this.logsSignal.set([]);
    this.totalSegundosSignal.set(0);
    this.segundosDecorridosSignal.set(0);
    this.feeling.set(null);
    this.generalNotes.set(null);
    this.avisoTocadoSignal.set(false);
    this.execucaoToken++;
    this.estadoSignal.set('running');
    this.cronometroHandle = setInterval(() => {
      this.segundosDecorridosSignal.update((v) => v + 1);
    }, 1000);
  }

  /** Encerra a sessao livre e vai pra revisao - equivalente a "confirmar o ultimo
   * exercicio" do fluxo guiado, so que sem log de exercicio nenhum pra capturar (sessao
   * livre nunca tem exercicios). So tem efeito durante `'running'` (sessao livre nunca
   * entra em `'timeUp'`, ja que nao ha alvo de duracao pra cruzar). Chamado pelo `Esc` -
   * ao contrario do fluxo guiado, onde `Esc` durante a execucao abandona sem salvar, na
   * sessao livre `Esc` e a unica forma natural de "terminar" (nao ha exercicios pra
   * esgotar), entao leva pra revisao em vez de descartar. */
  finalizarLivre(): void {
    if (this.estadoSignal() !== 'running') {
      return;
    }
    this.totalSegundosSignal.set(this.segundosDecorridosSignal());
    this.pararExecucaoAtual();
    this.estadoSignal.set('reviewing');
  }

  /** Confirma o exercicio atual (so tem efeito relevante durante `'running'`/`'timeUp'`
   * - fora disso e um no-op) e avanca pro proximo, ou vai pra revisao (`'reviewing'`) se
   * era o ultimo. Usado pelo `Enter` e reaproveitado pela seta `→`.
   *
   * Captura o log (`exerciseId`/`achievedBpm`/`actualDurationSeconds`) e soma o tempo do
   * exercicio confirmado ANTES de `pararExecucaoAtual()`/avancar pro proximo - depois
   * disso `segundosDecorridos` e reiniciado pro proximo exercicio. */
  confirmarEAvancar(): void {
    const estado = this.estadoSignal();
    if (estado !== 'running' && estado !== 'timeUp') {
      return;
    }

    const exercicioConfirmado = this.exerciciosSignal()[this.indiceAtualSignal()];
    if (exercicioConfirmado) {
      const segundos = this.segundosDecorridosSignal();
      this.logsSignal.update((logs) => [
        ...logs,
        {
          exerciseId: exercicioConfirmado.id,
          achievedBpm: this.metronome.bpm(),
          actualDurationSeconds: segundos,
          notes: null,
        },
      ]);
      this.totalSegundosSignal.update((v) => v + segundos);
    }

    this.pararExecucaoAtual();
    const proximoIndice = this.indiceAtualSignal() + 1;
    if (proximoIndice < this.exerciciosSignal().length) {
      this.indiceAtualSignal.set(proximoIndice);
      this.iniciarExercicioAtual();
    } else {
      this.estadoSignal.set('reviewing');
    }
  }

  /** Navegacao manual `←` - so faz sentido se ja passamos do primeiro exercicio, e so
   * enquanto a sessao ainda esta em andamento (fora de `'idle'`/`'reviewing'`).
   * Reinicia o count-in do exercicio anterior (nao tenta retomar o tempo decorrido
   * dele, nem desfaz um log ja confirmado - navegacao manual e best-effort). */
  voltarExercicio(): void {
    const estado = this.estadoSignal();
    if (estado === 'idle' || estado === 'reviewing' || this.indiceAtualSignal() === 0) {
      return;
    }
    this.pararExecucaoAtual();
    this.indiceAtualSignal.update((v) => v - 1);
    this.iniciarExercicioAtual();
  }

  /**
   * Monta o `CreateExecutionRequest` pronto pra `POST /api/executions` a partir do
   * estado acumulado da sessao (`logs`/`totalSegundos`/`feeling`/`generalNotes`).
   * Decisao de design: este servico fica deliberadamente sem depender de `ApiService`/
   * HTTP - so monta o objeto de dados puro. Quem chama (`SessionPage`) decide quando
   * chamar `ApiService.createExecution(...)` de fato e o que fazer com o resultado
   * (navegar pro Dashboard em sucesso, mostrar erro sem perder os dados em falha) -
   * mantem o `SessionStateService` focado na maquina de estados + acumulo de dados,
   * sem misturar responsabilidade de rede/navegacao. */
  buildExecutionRequest(trainingId: number | null, goalId: number | null): CreateExecutionRequest {
    return {
      trainingId,
      executionDate: new Date().toISOString().slice(0, 10),
      actualDurationMinutes: Math.round(this.totalSegundosSignal() / 60),
      feeling: this.feeling(),
      generalNotes: this.generalNotes()?.trim() || null,
      goalId,
      logs: this.logsSignal(),
    };
  }

  /** Espaco - pausa/retoma o clique do metronomo manualmente, sem sair do exercicio
   * (nao afeta o cronometro de tempo decorrido, que continua contando). */
  alternarMetronomo(): void {
    this.metronome.alternar();
  }

  /** Para tudo (cronometro + metronomo) e volta a `'idle'` - chamado pelo `Escape`. */
  encerrar(): void {
    this.pararExecucaoAtual();
    this.estadoSignal.set('idle');
  }

  /** Limpeza final - chamado pela `SessionPage` no `DestroyRef.onDestroy`, cobre tanto a
   * saida via `Escape` (que ja chama `encerrar()`) quanto o usuario navegando pra outro
   * lugar no meio de uma sessao (ex. clicando num link) sem passar por `encerrar()`. */
  destruir(): void {
    this.pararExecucaoAtual();
  }

  /** Prepara o exercicio em `indiceAtual`. `TOCA_JUNTO`: count-in do metronomo -> loop
   * continuo + cronometro. `TRANSCRICAO` (trabalho de ouvido): sem count-in nem metronomo -
   * vai direto pro cronometro (o usuario liga o metronomo na mao, `Espaco`, se quiser).
   * Privado - so mexe no exercicio "atual" (indice ja deve estar correto antes de chamar). */
  private iniciarExercicioAtual(): void {
    const exercicio = this.exerciciosSignal()[this.indiceAtualSignal()];
    if (!exercicio) {
      return;
    }

    const token = ++this.execucaoToken;
    this.segundosDecorridosSignal.set(0);
    this.avisoTocadoSignal.set(false);

    if (exercicio.kind === 'TRANSCRICAO') {
      // Nada de count-in nem clique: transcricao e ouvir uma gravacao e marcar trechos,
      // nao tocar pra um metronomo. Vai direto pro cronometro. Deixa o BPM no andamento
      // alvo pra, SE o usuario ligar o metronomo na mao (`Espaco`), ja sair no tempo certo.
      this.metronome.parar();
      if (exercicio.targetBpm != null) {
        this.metronome.bpm.set(exercicio.targetBpm);
      }
      this.estadoSignal.set('running');
      this.iniciarCronometro(exercicio);
      return;
    }

    const bpmAlvo = exercicio.targetBpm ?? BPM_FALLBACK;

    // O metronomo do exercicio herda a formula de compasso e a subdivisao da grade do
    // proprio `pattern`: um exercicio escrito em tercinas ganha clique em tercinas, nao em
    // seminimas. Sem pattern (`TOCA_JUNTO` ainda sem grade): compasso de fallback e clique
    // no pulso. So define o PONTO DE PARTIDA por exercicio - o usuario ainda pode trocar a
    // subdivisao no meio pelo overlay do metronomo.
    const pattern = exercicio.pattern;
    const compassoAlvo = pattern
      ? Math.max(1, Math.floor(pattern.timeSignature[0]))
      : COMPASSO_FALLBACK;
    this.metronome.subdivisao.set(
      pattern
        ? subdivisaoDoPattern(
            pattern.stepsPerBeat,
            pattern.tuplet,
            Object.values(pattern.hits)
              .flat()
              .filter((n): n is number => Number.isFinite(n)),
          )
        : 'quarter',
    );

    this.estadoSignal.set('countIn');

    void this.metronome.tocarCountIn(bpmAlvo, compassoAlvo).then(() => {
      if (token !== this.execucaoToken) {
        // Uma nova execucao (avancar/voltar/encerrar/destruir) ja substituiu esta. O loop
        // continuo que este count-in encadeou por baixo (ver `MetronomeService.
        // tocarCountIn`) ja foi parado - ou substituido por um novo, pro proximo
        // exercicio - por `pararExecucaoAtual()` antes disso, entao nao ha nada a fazer
        // aqui alem de nao pisar no estado atual.
        return;
      }
      this.segundosDecorridosSignal.set(0);
      this.estadoSignal.set('running');
      this.iniciarCronometro(exercicio);
    });
  }

  /** Liga o cronometro de 1s do exercicio atual: conta `segundosDecorridos` e, ao cruzar
   * `targetDurationSeconds`, dispara UMA vez o aviso de "tempo previsto cumprido"
   * (estado + som avulso, sem forcar avanco). */
  private iniciarCronometro(exercicio: Exercise): void {
    this.cronometroHandle = setInterval(() => {
      this.segundosDecorridosSignal.update((v) => v + 1);

      const alvo = exercicio.targetDurationSeconds;
      if (alvo != null && this.segundosDecorridosSignal() >= alvo && !this.avisoTocadoSignal()) {
        this.avisoTocadoSignal.set(true);
        this.estadoSignal.set('timeUp');
        this.metronome.avisoTempoCumprido();
      }
    }, 1000);
  }

  /** Para o cronometro (se rodando) e o metronomo, e invalida qualquer `tocarCountIn`
   * pendente (via `execucaoToken`) - passo comum antes de trocar de exercicio ou sair da
   * sessao. */
  private pararExecucaoAtual(): void {
    this.execucaoToken++;
    if (this.cronometroHandle !== null) {
      clearInterval(this.cronometroHandle);
      this.cronometroHandle = null;
    }
    this.metronome.parar();
  }
}
