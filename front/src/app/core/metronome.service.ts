import { Injectable, signal } from '@angular/core';

/** Subdivisao do pulso do metronomo - cada uma multiplica quantos cliques soam por tempo. */
export type Subdivisao = 'quarter' | 'eighth' | 'triplet';

const SUBDIVISION_MULTIPLIER: Record<Subdivisao, number> = {
  quarter: 1,
  eighth: 2,
  triplet: 3,
};

/** Janela de lookahead: o scheduler agenda no AudioContext qualquer clique cujo horario
 * caia dentro dos proximos N segundos a partir de "agora" (`audioContext.currentTime`). */
const LOOKAHEAD_SECONDS = 0.1;

/** Intervalo do loop de verificacao (`setInterval`). Curto o bastante pra nao deixar
 * buracos maiores que a janela de lookahead, mas nao tao curto a ponto de sobrecarregar
 * a thread principal - 25ms e o valor classico do artigo "A Tale of Two Clocks". */
const SCHEDULER_INTERVAL_MS = 25;

/** Duracao minima garantida do count-in, em segundos. */
const COUNT_IN_MIN_SECONDS = 5;

/** Duracao do envelope de volume de cada clique (attack+release), em segundos. */
const CLICK_DURATION_SECONDS = 0.04;

/** Nivel de um clique do metronomo - decide volume (e, no acento, tambem o timbre):
 * `'acento'` = primeiro tempo do compasso (mais alto/agudo), `'tempo'` = demais tempos
 * cheios (volume normal), `'subdivisao'` = tick intermediario quando ha subdivisao
 * (colcheia/tercina) - bem mais baixo, pra soar "PA pa pa PA pa pa" e nao um tapete
 * uniforme de cliques. */
export type NivelClique = 'acento' | 'tempo' | 'subdivisao';

/** Pico do envelope de ganho por nivel de clique. */
const CLICK_GAIN: Record<NivelClique, number> = {
  acento: 0.9,
  tempo: 0.55,
  subdivisao: 0.22,
};

/** Duracao (attack+release) de cada tom do aviso de "tempo previsto cumprido" (Fase
 * 3e-2), em segundos - bem mais longo que `CLICK_DURATION_SECONDS` pra nao soar como
 * mais um clique do metronomo. */
const AVISO_TOM_DURATION_SECONDS = 0.12;

/** Intervalo entre os dois tons do aviso de tempo cumprido, em segundos. */
const AVISO_TOM_INTERVALO_SECONDS = 0.14;

/**
 * Calcula, de forma pura (sem AudioContext/DOM), quantos compassos inteiros e quantos
 * segundos totais um count-in precisa ter pra garantir pelo menos `minSeconds` de
 * duracao. Extraida do agendamento de audio em si pra ser testavel isoladamente (Vitest).
 */
export function calcularCountIn(
  bpmAlvo: number,
  compassoAlvo: number,
  minSeconds = COUNT_IN_MIN_SECONDS,
): { duracaoCompassoSegundos: number; numeroDeCompassos: number; duracaoTotalSegundos: number } {
  const duracaoCompassoSegundos = (compassoAlvo * 60) / bpmAlvo;
  const numeroDeCompassos = Math.max(1, Math.ceil(minSeconds / duracaoCompassoSegundos));
  const duracaoTotalSegundos = numeroDeCompassos * duracaoCompassoSegundos;
  return { duracaoCompassoSegundos, numeroDeCompassos, duracaoTotalSegundos };
}

/**
 * Motor de metronomo compartilhado (singleton). Um unico `AudioContext`, criado sob
 * demanda no primeiro `iniciar()`/`tocarCountIn()` (nunca no construtor - muitos
 * browsers bloqueiam criacao de AudioContext antes de uma interacao real do usuario).
 *
 * Scheduler com lookahead (tecnica classica de Chris Wilson, "A Tale of Two Clocks"):
 * um `setInterval` curto (`SCHEDULER_INTERVAL_MS`) verifica repetidamente se o proximo
 * tempo do metronomo cai dentro de uma janela de lookahead (`LOOKAHEAD_SECONDS`) medida
 * contra `audioContext.currentTime`; quando cai, o clique e agendado com
 * `OscillatorNode.start(momentoExatoNoAudioContext)` - o agendamento do som em si roda
 * na linha do tempo precisa do AudioContext, so o loop de verificacao usa `setInterval`
 * (que sozinho teria drift/jitter demais pra manter o tempo).
 *
 * Cliques sao sintetizados via `OscillatorNode` + `GainNode` (sem arquivo de audio pra
 * gerenciar) - tom mais agudo/alto no primeiro tempo do compasso quando
 * `acentuarPrimeiroTempo` estiver ativo.
 *
 * Reusado (fases futuras) pelo metronomo embutido do Modo Sessao e pelo count-in de
 * exercicio - nenhuma logica de audio duplicada.
 */
@Injectable({ providedIn: 'root' })
export class MetronomeService {
  readonly bpm = signal(100);
  readonly compasso = signal(4);
  readonly subdivisao = signal<Subdivisao>('quarter');
  readonly acentuarPrimeiroTempo = signal(true);

  private readonly tocandoSignal = signal(false);
  readonly tocando = this.tocandoSignal.asReadonly();

  private readonly tempoAtualSignal = signal(0);
  readonly tempoAtual = this.tempoAtualSignal.asReadonly();

  private audioContext: AudioContext | null = null;

  /** Timestamp (audioContext.currentTime) do proximo "tick" (clique) a agendar - um tick
   * e uma subdivisao individual, pode ser mais fino que um tempo (beat) do compasso. */
  private nextTickTime = 0;
  /** Timestamp (audioContext.currentTime) em que o loop continuo atual COMECOU - so pra
   * `temposDecorridosNoLoop()` (playhead da drum sheet). `null` fora do loop continuo.
   * Setado junto com `nextTickTime` em `iniciarLoopContinuo`; zerado em `parar()`. */
  private loopStartTime: number | null = null;
  /** Indice do proximo tick dentro do compasso, contando subdivisoes (0-based). */
  private nextTickIndex = 0;
  private schedulerHandle: ReturnType<typeof setInterval> | null = null;

  private readonly tapTimestamps: number[] = [];

  /** Cria (ou retoma, se suspenso) o AudioContext compartilhado. So deve ser chamado a
   * partir de um metodo disparado por interacao do usuario (click/keydown). */
  private ensureAudioContext(): AudioContext {
    if (!this.audioContext) {
      this.audioContext = new AudioContext();
    }
    if (this.audioContext.state === 'suspended') {
      void this.audioContext.resume();
    }
    return this.audioContext;
  }

  /** Inicia o metronomo continuo, usando os signals `bpm`/`compasso`/`subdivisao`
   * atuais (reage a mudancas neles enquanto toca, ja que o scheduler le os signals a
   * cada iteracao do loop, nao captura um valor fixo no momento de `iniciar()`). Inicio
   * "fresco", com ~50ms de antecedencia (comportamento normal de qualquer metronomo). */
  iniciar(): void {
    if (this.tocandoSignal()) {
      return;
    }
    const ctx = this.ensureAudioContext();
    this.iniciarLoopContinuo(ctx, ctx.currentTime + 0.05);
  }

  /** Seeda o loop continuo do scheduler (`nextTickTime`/`nextTickIndex`) pra comecar
   * exatamente em `atTime` (tempo do AudioContext) e liga o `setInterval` que checa a
   * janela de lookahead. Compartilhada por `iniciar()` (inicio fresco, guardado por
   * `tocandoSignal()`) e por `tocarCountIn` (que encadeia o loop pra comecar exatamente
   * onde o count-in termina - por isso nao pode passar pelo guard de `iniciar()`, ja que
   * `tocandoSignal()` ja esta `true` durante o count-in). */
  private iniciarLoopContinuo(ctx: AudioContext, atTime: number): void {
    this.tocandoSignal.set(true);
    this.nextTickIndex = 0;
    this.nextTickTime = atTime;
    this.loopStartTime = atTime;
    this.tempoAtualSignal.set(0);
    this.schedulerHandle = setInterval(() => this.schedulerTick(), SCHEDULER_INTERVAL_MS);
  }

  parar(): void {
    if (this.schedulerHandle !== null) {
      clearInterval(this.schedulerHandle);
      this.schedulerHandle = null;
    }
    this.loopStartTime = null;
    this.tocandoSignal.set(false);
  }

  /**
   * Numero (fracionario) de tempos/beats decorridos desde o inicio do loop continuo
   * atual - fonte do playhead da drum sheet no Modo Sessao (ADR-0011, Fase 4d).
   *
   * Puramente ADITIVO e sem efeito colateral: le `audioContext.currentTime`, `bpm()` e
   * `loopStartTime` (este ultimo setado junto com `nextTickTime` em
   * `iniciarLoopContinuo`). Nao mexe no scheduler nem na semantica de `iniciar`/`parar`/
   * `tocarCountIn`. Reage a mudancas de `bpm()` naturalmente (o consumidor le a cada
   * frame de rAF).
   *
   * Retorna `null` quando nao esta tocando ou nao ha AudioContext. Durante o count-in
   * `loopStartTime` ja aponta pro instante FUTURO em que o loop continuo comeca (o
   * encadeamento e sincrono em `tocarCountIn`), entao o valor pode ser negativo ate o
   * count-in terminar - a `SessionPage` forca `pos = 0` enquanto no estado `'countIn'`.
   */
  temposDecorridosNoLoop(): number | null {
    const ctx = this.audioContext;
    if (!ctx || !this.tocandoSignal() || this.loopStartTime === null) {
      return null;
    }
    return ((ctx.currentTime - this.loopStartTime) * this.bpm()) / 60;
  }

  alternar(): void {
    if (this.tocandoSignal()) {
      this.parar();
    } else {
      this.iniciar();
    }
  }

  /** Chamado a cada tap do usuario. Calcula o BPM pela media dos intervalos entre os
   * ultimos taps, descartando (reiniciando a sequencia) quando o intervalo desde o
   * ultimo tap for grande demais (> 2s) - tratado como um novo inicio em vez de entrar
   * na media. Atualiza o signal `bpm` diretamente. */
  tapTempo(): void {
    const now = performance.now() / 1000;
    const last = this.tapTimestamps.at(-1);
    if (last !== undefined && now - last > 2) {
      this.tapTimestamps.length = 0;
    }
    this.tapTimestamps.push(now);
    // Mantem so os ultimos taps relevantes pra media (evita que um tap muito antigo,
    // ainda dentro da janela de 2s, distorca a leitura atual).
    if (this.tapTimestamps.length > 8) {
      this.tapTimestamps.shift();
    }
    if (this.tapTimestamps.length < 2) {
      return;
    }
    const intervals: number[] = [];
    for (let i = 1; i < this.tapTimestamps.length; i++) {
      intervals.push(this.tapTimestamps[i] - this.tapTimestamps[i - 1]);
    }
    const avgInterval = intervals.reduce((a, b) => a + b, 0) / intervals.length;
    const bpm = Math.round(60 / avgInterval);
    this.bpm.set(Math.min(300, Math.max(30, bpm)));
  }

  /**
   * Toca um count-in isolado no BPM/compasso informados (nao usa os signals atuais -
   * o count-in de um exercicio pode ter um BPM diferente do metronomo avulso). Toca por
   * um numero inteiro de compassos, garantindo pelo menos 5s no total (ver
   * `calcularCountIn`).
   *
   * Ao agendar o ultimo clique do count-in, ENCADEIA o loop continuo (mesmo scheduler
   * usado por `iniciar()`, via `iniciarLoopContinuo`) pra comecar exatamente em
   * `proximoBeatTime` - o instante em que o compasso do count-in termina - sem gap nem
   * reinicio: o primeiro tempo do exercicio de verdade e so mais um passo do mesmo
   * compasso que ja vinha tocando (ex: dois compassos de 6s tocam os 6s inteiros e o
   * treino so continua no tempo seguinte, sem hiato nem reinicio). O encadeamento e
   * seedado de forma SINCRONA logo apos agendar os cliques do count-in (nao espera eles
   * soarem de verdade) - o `schedulerTick` so agenda som quando `proximoBeatTime` entrar
   * na janela de lookahead, entao nada toca adiantado; a precisao vem inteira do relogio
   * do AudioContext, nao de timers do JS. Deixa `bpm`/`compasso` do metronomo ja
   * configurados pro loop que se segue.
   *
   * Atualiza `tocando`/`tempoAtual` durante a execucao (mesmo padrao de
   * `scheduleUiUpdate` usado pelo loop continuo em `schedulerTick`) - assim qualquer UI
   * que ja saiba desenhar o pulso do metronomo (pontinhos por tempo) funciona identica
   * durante o count-in, sem logica duplicada.
   *
   * A Promise resolve quando o ultimo clique do count-in termina de soar - quem chama
   * (ex. `SessionStateService`) usa isso so pra saber a hora certa de trocar a UI de
   * `'countIn'` pra `'running'`; o audio em si ja esta seguindo ininterrupto por baixo,
   * independente de quando a Promise resolve.
   */
  async tocarCountIn(bpmAlvo: number, compassoAlvo: number): Promise<void> {
    const ctx = this.ensureAudioContext();
    const { numeroDeCompassos } = calcularCountIn(bpmAlvo, compassoAlvo);
    const totalBeats = numeroDeCompassos * compassoAlvo;
    const beatDuration = 60 / bpmAlvo;
    const startTime = ctx.currentTime + 0.05;
    const proximoBeatTime = startTime + totalBeats * beatDuration;

    this.tocandoSignal.set(true);
    this.tempoAtualSignal.set(0);

    for (let i = 0; i < totalBeats; i++) {
      const beatIndexInBar = i % compassoAlvo;
      const when = startTime + i * beatDuration;
      this.scheduleClick(ctx, when, beatIndexInBar === 0 ? 'acento' : 'tempo');
      this.scheduleUiUpdate(ctx, when, beatIndexInBar);
    }

    this.bpm.set(bpmAlvo);
    this.compasso.set(compassoAlvo);
    this.iniciarLoopContinuo(ctx, proximoBeatTime);

    const waitMs = Math.max(0, (proximoBeatTime - ctx.currentTime) * 1000);
    await new Promise<void>((resolve) => setTimeout(resolve, waitMs));
  }

  /**
   * Toca o aviso sonoro de "tempo previsto cumprido" (Fase 3e-2, Modo Sessao) - dois
   * tons curtos em sequencia, timbre senoidal numa frequencia bem diferente das dos
   * cliques normais/acentuados (`scheduleClick` usa `square` em 900/1400Hz - aqui e
   * `sine` em 660/880Hz, com envelope mais longo) - pra nao ser confundido com mais um
   * clique mesmo por quem esta de costas pra tela, como pediu o usuario. Execucao
   * avulsa disparada uma unica vez pelo `SessionStateService` ao cruzar o limiar: nao
   * mexe no scheduler continuo nem nos signals `tocando`/`tempoAtual` - o metronomo pode
   * estar tocando (ou nao) ao mesmo tempo, sem interferencia.
   */
  avisoTempoCumprido(): void {
    const ctx = this.ensureAudioContext();
    const startTime = ctx.currentTime + 0.02;
    this.scheduleAvisoTom(ctx, startTime, 660);
    this.scheduleAvisoTom(ctx, startTime + AVISO_TOM_INTERVALO_SECONDS, 880);
  }

  /** Gera e agenda um unico tom do aviso de tempo cumprido - mesma tecnica de envelope
   * rapido de ataque/liberacao de `scheduleClick`, mantida numa funcao separada pra nao
   * misturar os dois timbres (clique do metronomo vs aviso de tempo). */
  private scheduleAvisoTom(ctx: AudioContext, when: number, frequency: number): void {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.value = frequency;

    gain.gain.setValueAtTime(0, when);
    gain.gain.linearRampToValueAtTime(0.5, when + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + AVISO_TOM_DURATION_SECONDS);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(when);
    osc.stop(when + AVISO_TOM_DURATION_SECONDS + 0.01);
  }

  private schedulerTick(): void {
    const ctx = this.audioContext;
    if (!ctx) {
      return;
    }
    const compasso = this.compasso();
    const multiplier = SUBDIVISION_MULTIPLIER[this.subdivisao()];
    const ticksPerBar = compasso * multiplier;
    const secondsPerTick = 60 / this.bpm() / multiplier;

    while (this.nextTickTime < ctx.currentTime + LOOKAHEAD_SECONDS) {
      const tickIndex = this.nextTickIndex;
      const isTempoCheio = tickIndex % multiplier === 0;
      // Tres niveis: acento no 1o tempo (se ligado), volume normal nos demais tempos
      // cheios, volume baixo nos ticks de subdivisao (colcheia/tercina).
      const nivel: NivelClique =
        tickIndex === 0 && this.acentuarPrimeiroTempo()
          ? 'acento'
          : isTempoCheio
            ? 'tempo'
            : 'subdivisao';
      this.scheduleClick(ctx, this.nextTickTime, nivel);

      // `tempoAtual` acompanha o TEMPO (beat) do compasso, nao a subdivisao - so avanca
      // nos ticks que coincidem com o inicio de um tempo cheio (0-based, 0..compasso-1),
      // pra alimentar os pontinhos de pulso na UI independente da subdivisao escolhida.
      if (isTempoCheio) {
        this.scheduleUiUpdate(ctx, this.nextTickTime, Math.floor(tickIndex / multiplier));
      }

      this.nextTickTime += secondsPerTick;
      this.nextTickIndex = (this.nextTickIndex + 1) % ticksPerBar;
    }
  }

  /** Agenda a atualizacao do signal `tempoAtual` (pulso visual) pra acontecer o mais
   * proximo possivel do instante real do clique, via `setTimeout` relativo ao momento
   * atual do AudioContext - so pra UI, nao afeta a precisao do agendamento sonoro. */
  private scheduleUiUpdate(ctx: AudioContext, when: number, beatIndex: number): void {
    const delayMs = Math.max(0, (when - ctx.currentTime) * 1000);
    setTimeout(() => {
      if (this.tocandoSignal()) {
        this.tempoAtualSignal.set(beatIndex);
      }
    }, delayMs);
  }

  /** Gera e agenda um unico clique sintetizado (OscillatorNode + GainNode) no instante
   * `when` da linha do tempo do AudioContext. O `nivel` decide o volume (pico do
   * envelope de ganho, ver `CLICK_GAIN`); o acento tambem sobe o timbre pra 1400Hz. */
  private scheduleClick(ctx: AudioContext, when: number, nivel: NivelClique): void {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'square';
    osc.frequency.value = nivel === 'acento' ? 1400 : 900;

    const peakGain = CLICK_GAIN[nivel];
    // Envelope rapido de ataque/liberacao pra evitar clique/estouro (pop) no on/off do
    // oscilador - sobe quase instantaneo, desce em CLICK_DURATION_SECONDS.
    gain.gain.setValueAtTime(0, when);
    gain.gain.linearRampToValueAtTime(peakGain, when + 0.002);
    gain.gain.exponentialRampToValueAtTime(0.0001, when + CLICK_DURATION_SECONDS);

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.start(when);
    osc.stop(when + CLICK_DURATION_SECONDS + 0.005);
  }
}
