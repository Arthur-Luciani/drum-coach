import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  MetronomeService,
  calcularCountIn,
  cliquesPorTempoDoPattern,
  subdivisaoDoPattern,
} from './metronome.service';

/** Dublês minimos dos nós Web Audio usados por `MetronomeService` - registram o que foi
 * chamado (tipo de onda, frequencia, start/stop) sem depender de um `AudioContext` real
 * (jsdom/vitest nao implementa a Web Audio API). */
class FakeGainNode {
  readonly gain = {
    setValueAtTime: vi.fn(),
    linearRampToValueAtTime: vi.fn(),
    exponentialRampToValueAtTime: vi.fn(),
  };
  connect(): void {}
}

class FakeOscillatorNode {
  type = '';
  frequency = { value: 0 };
  startedAt: number | null = null;
  stoppedAt: number | null = null;
  connect(): void {}
  start(when: number): void {
    this.startedAt = when;
  }
  stop(when: number): void {
    this.stoppedAt = when;
  }
}

class FakeAudioContext {
  currentTime = 0;
  state = 'running';
  readonly createdOscillators: FakeOscillatorNode[] = [];
  readonly createdGains: FakeGainNode[] = [];

  createOscillator(): FakeOscillatorNode {
    const osc = new FakeOscillatorNode();
    this.createdOscillators.push(osc);
    return osc;
  }

  createGain(): FakeGainNode {
    const gain = new FakeGainNode();
    this.createdGains.push(gain);
    return gain;
  }

  resume(): Promise<void> {
    return Promise.resolve();
  }
}

/**
 * Testa so a matematica pura do count-in (duracao de compasso, numero de compassos,
 * duracao total) - nao depende de AudioContext/DOM, isolada de proposito do agendamento
 * de audio em `MetronomeService.tocarCountIn`.
 */
describe('calcularCountIn', () => {
  it('100 bpm, 4/4: compasso de 2.4s -> 3 compassos (7.2s), >= 5s', () => {
    const resultado = calcularCountIn(100, 4);
    expect(resultado.duracaoCompassoSegundos).toBeCloseTo(2.4, 6);
    expect(resultado.numeroDeCompassos).toBe(3);
    expect(resultado.duracaoTotalSegundos).toBeCloseTo(7.2, 6);
    expect(resultado.duracaoTotalSegundos).toBeGreaterThanOrEqual(5);
  });

  it('60 bpm, 4/4: compasso de 4s -> 2 compassos (8s)', () => {
    const resultado = calcularCountIn(60, 4);
    expect(resultado.duracaoCompassoSegundos).toBeCloseTo(4, 6);
    expect(resultado.numeroDeCompassos).toBe(2);
    expect(resultado.duracaoTotalSegundos).toBeCloseTo(8, 6);
  });

  it('200 bpm, 4/4: compasso de 1.2s -> 5 compassos (6s), minimo 1 compasso respeitado', () => {
    const resultado = calcularCountIn(200, 4);
    expect(resultado.duracaoCompassoSegundos).toBeCloseTo(1.2, 6);
    expect(resultado.numeroDeCompassos).toBe(5);
    expect(resultado.duracaoTotalSegundos).toBeCloseTo(6, 6);
  });

  it('40 bpm, 3/4: compasso ja dura mais que 5s -> minimo de 1 compasso', () => {
    // duracao do compasso = 3 * 60 / 40 = 4.5s -> ainda < 5s, precisa de 2 compassos.
    const resultado = calcularCountIn(40, 3);
    expect(resultado.duracaoCompassoSegundos).toBeCloseTo(4.5, 6);
    expect(resultado.numeroDeCompassos).toBe(2);
    expect(resultado.duracaoTotalSegundos).toBeCloseTo(9, 6);
  });

  it('bpm baixo o bastante pra 1 compasso ja passar de 5s (30 bpm, 12/8 hipotetico)', () => {
    // duracao do compasso = 12 * 60 / 30 = 24s -> so precisa de 1 compasso.
    const resultado = calcularCountIn(30, 12);
    expect(resultado.duracaoCompassoSegundos).toBeCloseTo(24, 6);
    expect(resultado.numeroDeCompassos).toBe(1);
    expect(resultado.duracaoTotalSegundos).toBeCloseTo(24, 6);
    expect(resultado.duracaoTotalSegundos).toBeGreaterThanOrEqual(5);
  });

  it('respeita um minSeconds customizado', () => {
    const resultado = calcularCountIn(120, 4, 10);
    // duracao do compasso = 4*60/120 = 2s -> precisa de 5 compassos pra >= 10s.
    expect(resultado.numeroDeCompassos).toBe(5);
    expect(resultado.duracaoTotalSegundos).toBeCloseTo(10, 6);
  });
});

/**
 * `cliquesPorTempoDoPattern` - a subdivisao REAL do exercicio como numero (base tanto da
 * subdivisao de clique quanto da janela adaptativa da pauta).
 */
describe('cliquesPorTempoDoPattern', () => {
  it('sem hits: usa o stepsPerBeat declarado', () => {
    expect(cliquesPorTempoDoPattern(1)).toBe(1);
    expect(cliquesPorTempoDoPattern(2)).toBe(2);
    expect(cliquesPorTempoDoPattern(4)).toBe(4);
  });

  it('usa a posicao real das notas, nao a resolucao da grade', () => {
    // grade de 16, mas notas so em posicoes de colcheia -> 2 cliques/tempo
    expect(cliquesPorTempoDoPattern(4, [0, 2, 4, 6, 8, 10, 12, 14])).toBe(2);
    // uma nota fora da grade de colcheia -> volta pra 4
    expect(cliquesPorTempoDoPattern(4, [0, 2, 5])).toBe(4);
    // tercina real
    expect(cliquesPorTempoDoPattern(3, [0, 1, 2, 3, 4, 5])).toBe(3);
  });

  it('hit no indice 0 nao restringe; so a cabeca do compasso -> 1', () => {
    expect(cliquesPorTempoDoPattern(4, [0])).toBe(1);
    expect(cliquesPorTempoDoPattern(4, [0, 8, 16])).toBe(1);
  });

  it('subdivisao prima (quintina) fica como esta', () => {
    expect(cliquesPorTempoDoPattern(5, [0, 1, 2, 3, 4])).toBe(5);
  });
});

/**
 * `subdivisaoDoPattern` - deriva a subdivisao de clique do metronomo da grade ritmica de
 * um `DrumPattern`, pra o Modo Sessao fazer o exercicio herdar a subdivisao em que foi
 * escrito (ver `SessionStateService.iniciarExercicioAtual`).
 */
describe('subdivisaoDoPattern', () => {
  it('sem hits: cai no stepsPerBeat declarado', () => {
    expect(subdivisaoDoPattern(1, false)).toBe('quarter');
    expect(subdivisaoDoPattern(2, false)).toBe('eighth');
    expect(subdivisaoDoPattern(3, true)).toBe('triplet');
    expect(subdivisaoDoPattern(4, false)).toBe('sixteenth');
  });

  it('usa a subdivisao REAL das notas, nao a resolucao da grade', () => {
    // Grade de semicolcheia (stepsPerBeat 4) mas notas so em posicoes de colcheia
    // (indices pares) -> clique em colcheia, nao em semicolcheia. Bug do "Banco de
    // viradas": groove todo em colcheias numa grade de 16 tocava clique em 4/tempo.
    const soPares = [0, 2, 4, 6, 8, 10, 12, 14, 46, 48, 50];
    expect(subdivisaoDoPattern(4, false, soPares)).toBe('eighth');

    // Uma unica nota fora da grade de colcheia (indice impar) -> volta pra semicolcheia.
    expect(subdivisaoDoPattern(4, false, [0, 2, 4, 7])).toBe('sixteenth');

    // Tercina: 3 steps/tempo, notas em todos -> tercina.
    expect(subdivisaoDoPattern(3, true, [0, 1, 2, 3, 4, 5])).toBe('triplet');

    // Sextina escrita so nas posicoes de tercina (pares) -> tercina.
    expect(subdivisaoDoPattern(6, true, [0, 2, 4, 6, 8, 10])).toBe('triplet');
  });

  it('hit no indice 0 nao restringe (gcd(k, 0) = k)', () => {
    expect(subdivisaoDoPattern(4, false, [0])).toBe('quarter'); // so a cabeca do compasso
    expect(subdivisaoDoPattern(4, false, [0, 8, 16])).toBe('quarter'); // so tempos cheios
  });

  it('cliques/tempo sem casamento exato: maior divisor coerente (`tuplet` puxa pra base 3)', () => {
    expect(subdivisaoDoPattern(8, false, [0, 1, 2, 3, 4, 5, 6, 7])).toBe('sixteenth');
    expect(subdivisaoDoPattern(5, false, [0, 1, 2, 3, 4])).toBe('quarter'); // quintina: sem clique coerente
  });
});

/**
 * Testa `avisoTempoCumprido` (Fase 3e-2, aviso de "tempo previsto cumprido" do Modo
 * Sessao) injetando um `AudioContext` fake no lugar do real (`ensureAudioContext` cria
 * um `new AudioContext()` sob demanda - substituir o construtor global antes de chamar o
 * metodo e o suficiente, sem precisar de um `AudioContext` de verdade). Confirma que o
 * timbre/frequencia sao BEM diferentes do clique do metronomo (`scheduleClick` usa
 * `square` em 900/1400Hz) e que a chamada nao mexe nos signals do loop continuo.
 */
describe('MetronomeService.avisoTempoCumprido', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('agenda dois tons senoidais (nao quadrados) em frequencias bem diferentes das do clique', () => {
    const fakeCtx = new FakeAudioContext();
    // `AudioContext` e usado com `new` (`ensureAudioContext`) - um `vi.fn()` simples nao
    // e construtivel, precisa de uma classe/funcao de verdade que devolva o fake.
    vi.stubGlobal(
      'AudioContext',
      class {
        constructor() {
          return fakeCtx;
        }
      },
    );

    const service = new MetronomeService();
    service.avisoTempoCumprido();

    expect(fakeCtx.createdOscillators).toHaveLength(2);
    for (const osc of fakeCtx.createdOscillators) {
      // O clique do metronomo (scheduleClick) usa 'square' - o aviso tem que soar
      // diferente mesmo por quem esta de costas pra tela.
      expect(osc.type).toBe('sine');
      expect(osc.startedAt).not.toBeNull();
      expect(osc.stoppedAt).not.toBeNull();
    }

    const frequencias = fakeCtx.createdOscillators.map((o) => o.frequency.value);
    // Bem diferentes das frequencias do clique (900Hz normal / 1400Hz acentuado).
    expect(frequencias).toEqual([660, 880]);

    // Os dois tons tocam em sequencia (segundo depois do primeiro), nao ao mesmo tempo.
    const [primeiro, segundo] = fakeCtx.createdOscillators;
    expect(segundo.startedAt!).toBeGreaterThan(primeiro.startedAt!);
  });

  it('execucao avulsa - nao mexe nos signals tocando/tempoAtual do loop continuo', () => {
    const fakeCtx = new FakeAudioContext();
    // `AudioContext` e usado com `new` (`ensureAudioContext`) - um `vi.fn()` simples nao
    // e construtivel, precisa de uma classe/funcao de verdade que devolva o fake.
    vi.stubGlobal(
      'AudioContext',
      class {
        constructor() {
          return fakeCtx;
        }
      },
    );

    const service = new MetronomeService();
    service.avisoTempoCumprido();

    expect(service.tocando()).toBe(false);
    expect(service.tempoAtual()).toBe(0);
  });
});

/**
 * Testa a correcao do "gap" na transicao count-in -> loop continuo: antes, o loop
 * continuo so era seedado quando a Promise de `tocarCountIn` resolvia (via `setTimeout`,
 * sujeito a jitter do JS) e comecava sempre em `ctx.currentTime + 0.05` - um reinicio
 * independente do tempo real do ultimo clique do count-in, que podia soar como um
 * hiato/reinicio perceptivel. Agora `tocarCountIn` seeda o loop continuo de forma
 * SINCRONA (logo apos agendar os cliques do count-in) pra comecar exatamente no instante
 * em que o compasso do count-in termina - a precisao vem do relogio do AudioContext
 * (`osc.start(when)`), nao de quando o JS roda o callback.
 */
describe('MetronomeService.tocarCountIn', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('encadeia o loop continuo exatamente onde o count-in termina, sem gap nem reinicio', async () => {
    vi.useFakeTimers();
    const fakeCtx = new FakeAudioContext();
    vi.stubGlobal(
      'AudioContext',
      class {
        constructor() {
          return fakeCtx;
        }
      },
    );

    const service = new MetronomeService();
    // 120 bpm, 4/4: compasso de 2s -> calcularCountIn exige 3 compassos (6s) pra >= 5s.
    // totalBeats = 12, beatDuration = 0.5s, startTime = ctx.currentTime(0) + 0.05 = 0.05.
    const promise = service.tocarCountIn(120, 4);

    // bpm/compasso e o "tocando" do loop continuo ja ficam prontos de forma sincrona,
    // antes mesmo do count-in soar de verdade.
    expect(service.bpm()).toBe(120);
    expect(service.compasso()).toBe(4);
    expect(service.tocando()).toBe(true);

    expect(fakeCtx.createdOscillators).toHaveLength(12);
    const ultimoCliqueDoCountIn = fakeCtx.createdOscillators[11];
    expect(ultimoCliqueDoCountIn.startedAt).toBeCloseTo(0.05 + 11 * 0.5, 6); // 5.55

    // Avanca o relogio do AudioContext (fake) pra dentro da janela de lookahead do
    // proximo tempo - fixo em 6.0 (nao acompanha o avanco dos fake timers do JS abaixo,
    // so serve pra simular "o AudioContext esta em 6.0s" quando o scheduler checar).
    fakeCtx.currentTime = 6.0;
    // Avanca os fake timers o bastante pro scheduler (25ms) pegar o proximo clique E pro
    // `setTimeout` interno de `tocarCountIn` (~6050ms) resolver a Promise.
    await vi.advanceTimersByTimeAsync(6200);

    // O 13o clique (primeiro do loop continuo, ou seja do exercicio de verdade) cai
    // EXATAMENTE 0.5s (um tempo) depois do ultimo clique do count-in (5.55 + 0.5 = 6.05)
    // - independente de `ctx.currentTime` valer 6.0 no momento em que o scheduler rodou.
    expect(fakeCtx.createdOscillators).toHaveLength(13);
    const primeiroCliqueDoLoop = fakeCtx.createdOscillators[12];
    expect(primeiroCliqueDoLoop.startedAt).toBeCloseTo(6.05, 6);
    // E acentuado (primeiro tempo do compasso), como o count-in ja vinha fazendo.
    expect(primeiroCliqueDoLoop.frequency.value).toBe(1400);

    // Continua tocando ininterrupto - a Promise resolver nao encerra o loop continuo.
    await promise;
    expect(service.tocando()).toBe(true);
  });
});

/**
 * Testa o getter aditivo `temposDecorridosNoLoop()` (ADR-0011, Fase 4d - fonte do
 * playhead da drum sheet no Modo Sessao). Contrato: `null` parado / sem AudioContext;
 * numero (fracionario) de beats decorridos desde o inicio do loop continuo enquanto toca,
 * crescente com `ctx.currentTime` e escalado por `bpm()`.
 */
/**
 * Volume por nivel de clique: acento > tempo > subdivisao. Com subdivisao ativa
 * (colcheia/tercina) os ticks intermediarios tem que soar bem mais baixos que os tempos
 * cheios - "PA pa pa PA pa pa", nao um tapete uniforme.
 */
describe('MetronomeService.scheduleClick - niveis de volume', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('colcheia: acento no 1o tempo, volume normal nos tempos, volume baixo nas subdivisoes', () => {
    vi.useFakeTimers();
    const fakeCtx = new FakeAudioContext();
    vi.stubGlobal(
      'AudioContext',
      class {
        constructor() {
          return fakeCtx;
        }
      },
    );

    const service = new MetronomeService();
    service.bpm.set(120); // colcheia a 120 -> 0.25s por tick
    service.subdivisao.set('eighth'); // multiplier 2 -> 8 ticks/compasso em 4/4

    service.iniciar(); // seeda nextTickTime = 0.05
    // Coloca o relogio bem a frente pra o unico schedulerTick agendar um compasso inteiro
    // de uma vez (o `while` agenda tudo com nextTickTime < currentTime + lookahead).
    fakeCtx.currentTime = 2.5;
    vi.advanceTimersByTime(30); // > SCHEDULER_INTERVAL_MS (25)

    const pico = (i: number): number =>
      fakeCtx.createdGains[i].gain.linearRampToValueAtTime.mock.calls[0][0] as number;

    // ticks 0..7 = acento, sub, tempo, sub, tempo, sub, tempo, sub
    expect(pico(0)).toBeGreaterThan(pico(2)); // acento > tempo
    expect(pico(2)).toBeGreaterThan(pico(1)); // tempo > subdivisao
    expect(pico(1)).toBe(pico(3)); // subdivisoes iguais entre si
    expect(pico(1)).toBe(pico(5));
    expect(pico(2)).toBe(pico(4)); // tempos cheios iguais entre si
    expect(pico(2)).toBe(pico(6));
  });
});

describe('MetronomeService.temposDecorridosNoLoop', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('retorna null quando nao ha AudioContext / nao esta tocando', () => {
    const service = new MetronomeService();
    expect(service.temposDecorridosNoLoop()).toBeNull();
  });

  it('retorna beats crescentes durante o loop continuo, escalados pelo bpm, e volta a null ao parar', () => {
    const fakeCtx = new FakeAudioContext();
    vi.stubGlobal(
      'AudioContext',
      class {
        constructor() {
          return fakeCtx;
        }
      },
    );

    const service = new MetronomeService();
    service.bpm.set(120); // 120 bpm -> 2 beats por segundo

    service.iniciar(); // seeda loopStartTime = ctx.currentTime(0) + 0.05 = 0.05
    const t0 = service.temposDecorridosNoLoop();
    expect(t0).not.toBeNull();

    fakeCtx.currentTime = 2.05; // 2s depois do inicio do loop
    const t1 = service.temposDecorridosNoLoop()!;
    expect(t1).toBeGreaterThan(t0!);
    expect(t1).toBeCloseTo(4, 6); // 2s * 120/60 = 4 beats

    fakeCtx.currentTime = 5.05; // +3s -> +6 beats
    expect(service.temposDecorridosNoLoop()!).toBeCloseTo(10, 6);

    service.parar();
    expect(service.temposDecorridosNoLoop()).toBeNull();
  });
});
