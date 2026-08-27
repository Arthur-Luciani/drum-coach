import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MetronomeService } from '../../core/metronome.service';
import { Exercise } from '../../models';
import { SessionStateService } from './session-state.service';

/**
 * Dublê de `MetronomeService` - o Modo Sessao so le/chama uma fatia pequena da API
 * publica do metronomo (`bpm`/`compasso` como signals, `iniciar`/`parar`/`alternar`,
 * `tocarCountIn`, `avisoTempoCumprido`). Substituir por um fake evita depender de
 * `AudioContext`/tempo real (`tocarCountIn` de verdade demora pelo menos 5s) e deixa o
 * teste determinístico via `vi.useFakeTimers()`.
 *
 * `tocarCountIn` espelha o contrato real (ver `MetronomeService.tocarCountIn`): quem
 * ajusta `bpm`/`compasso` e "inicia" o loop continuo e o proprio metodo, encadeado
 * internamente ao fim do count-in - `SessionStateService` nao chama `iniciar()` a parte
 * mais (era assim ate a correcao do gap na transicao count-in -> exercicio).
 */
class FakeMetronomeService {
  readonly bpm = signal(100);
  readonly compasso = signal(4);

  readonly tocarCountInCalls: Array<{ bpm: number; compasso: number }> = [];
  iniciarCalls = 0;
  pararCalls = 0;
  alternarCalls = 0;
  avisoTempoCumpridoCalls = 0;

  tocarCountIn(bpmAlvo: number, compassoAlvo: number): Promise<void> {
    this.tocarCountInCalls.push({ bpm: bpmAlvo, compasso: compassoAlvo });
    this.bpm.set(bpmAlvo);
    this.compasso.set(compassoAlvo);
    this.iniciarCalls++;
    return Promise.resolve();
  }

  iniciar(): void {
    this.iniciarCalls++;
  }

  parar(): void {
    this.pararCalls++;
  }

  alternar(): void {
    this.alternarCalls++;
  }

  avisoTempoCumprido(): void {
    this.avisoTempoCumpridoCalls++;
  }
}

function criarExercicio(overrides: Partial<Exercise> & { id: number }): Exercise {
  return {
    trainingId: 1,
    name: `Exercício ${overrides.id}`,
    kind: 'TOCA_JUNTO',
    exerciseType: 'technique',
    howToExecute: null,
    pattern: null,
    passages: [],
    targetBpm: null,
    targetDurationSeconds: null,
    videoSourceType: null,
    videoUrl: null,
    videoFilePath: null,
    orderIndex: overrides.id,
    createdBy: 'USER',
    lastModifiedBy: 'USER',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

/** Avança o relogio fake e da chance a qualquer Promise pendente (ex: `tocarCountIn`) de
 * resolver antes de continuar - `vi.advanceTimersByTimeAsync` intercala timers e
 * microtasks, ao contrário da variante sincrona. */
async function flush(ms = 0): Promise<void> {
  await vi.advanceTimersByTimeAsync(ms);
}

describe('SessionStateService', () => {
  let service: SessionStateService;
  let metronome: FakeMetronomeService;

  beforeEach(() => {
    vi.useFakeTimers();
    metronome = new FakeMetronomeService();
    TestBed.configureTestingModule({
      providers: [SessionStateService, { provide: MetronomeService, useValue: metronome }],
    });
    service = TestBed.inject(SessionStateService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('ciclo completo: aviso de tempo cumprido (uma vez so) -> confirma -> proximo exercicio -> revisao', async () => {
    const ex1 = criarExercicio({ id: 1, targetBpm: 120, targetDurationSeconds: 3 });
    const ex2 = criarExercicio({ id: 2, targetBpm: 90, targetDurationSeconds: null });

    service.iniciar([ex1, ex2]);
    expect(service.estado()).toBe('countIn');

    // Resolve o count-in fake (Promise) e entra em 'running' no BPM do exercicio 1.
    await flush();
    expect(service.estado()).toBe('running');
    expect(metronome.tocarCountInCalls).toEqual([{ bpm: 120, compasso: 4 }]);
    expect(metronome.bpm()).toBe(120);
    expect(metronome.iniciarCalls).toBe(1);

    // 2s ainda nao bateu o alvo (3s) - sem aviso ainda.
    await flush(2000);
    expect(service.estado()).toBe('running');
    expect(service.segundosDecorridos()).toBe(2);
    expect(metronome.avisoTempoCumpridoCalls).toBe(0);

    // 3o segundo cruza o alvo - transicao pra 'timeUp', aviso sonoro disparado UMA vez.
    await flush(1000);
    expect(service.estado()).toBe('timeUp');
    expect(service.segundosDecorridos()).toBe(3);
    expect(metronome.avisoTempoCumpridoCalls).toBe(1);

    // Mais 2s rodando em 'timeUp' - nao dispara de novo, cronometro continua contando.
    await flush(2000);
    expect(service.estado()).toBe('timeUp');
    expect(service.segundosDecorridos()).toBe(5);
    expect(metronome.avisoTempoCumpridoCalls).toBe(1);

    // Enter/-> funciona normalmente em 'timeUp' (nao trava por causa do aviso).
    service.confirmarEAvancar();
    expect(service.logs()).toEqual([{ exerciseId: 1, achievedBpm: 120, actualDurationSeconds: 5, notes: null }]);
    expect(service.totalSegundos()).toBe(5);
    expect(service.indiceAtual()).toBe(1);
    expect(service.estado()).toBe('countIn');

    // Exercicio 2 (sem targetDurationSeconds) - roda 10s sem nunca virar 'timeUp'.
    await flush();
    expect(service.estado()).toBe('running');
    expect(metronome.bpm()).toBe(90);
    await flush(10_000);
    expect(service.estado()).toBe('running');
    expect(service.segundosDecorridos()).toBe(10);
    expect(metronome.avisoTempoCumpridoCalls).toBe(1);

    // Era o ultimo exercicio - confirma e vai pra revisao de verdade.
    service.confirmarEAvancar();
    expect(service.estado()).toBe('reviewing');
    expect(service.logs()).toEqual([
      { exerciseId: 1, achievedBpm: 120, actualDurationSeconds: 5, notes: null },
      { exerciseId: 2, achievedBpm: 90, actualDurationSeconds: 10, notes: null },
    ]);
    expect(service.totalSegundos()).toBe(15);

    // confirmarEAvancar/voltarExercicio viram no-op em 'reviewing' - nao muda mais nada.
    service.confirmarEAvancar();
    service.voltarExercicio();
    expect(service.estado()).toBe('reviewing');
    expect(service.logs().length).toBe(2);
  });

  it('buildExecutionRequest monta o CreateExecutionRequest com os dados acumulados, trimando notas vazias', async () => {
    const ex1 = criarExercicio({ id: 1, targetBpm: 100, targetDurationSeconds: null });
    service.iniciar([ex1]);
    await flush();
    await flush(7000);
    service.confirmarEAvancar();
    expect(service.estado()).toBe('reviewing');

    service.feeling.set('Bom');
    service.generalNotes.set('   nota com espaco   ');

    const request = service.buildExecutionRequest(42, 7);
    expect(request.trainingId).toBe(42);
    expect(request.goalId).toBe(7);
    expect(request.actualDurationMinutes).toBe(0); // Math.round(7/60) = 0
    expect(request.feeling).toBe('Bom');
    expect(request.generalNotes).toBe('nota com espaco');
    expect(request.logs).toEqual([{ exerciseId: 1, achievedBpm: 100, actualDurationSeconds: 7, notes: null }]);

    // generalNotes so em branco -> null (nao manda string vazia pra API).
    service.generalNotes.set('   ');
    expect(service.buildExecutionRequest(42, 7).generalNotes).toBeNull();
  });

  it('encerrar() para o metronomo e volta pra idle', async () => {
    const ex1 = criarExercicio({ id: 1 });
    service.iniciar([ex1]);
    await flush();
    expect(service.estado()).toBe('running');

    service.encerrar();
    expect(service.estado()).toBe('idle');
    expect(metronome.pararCalls).toBeGreaterThan(0);
  });

  it('alternarMetronomo() delega pro metronomo em qualquer estado do exercicio rodando', async () => {
    const ex1 = criarExercicio({ id: 1 });
    service.iniciar([ex1]);
    service.alternarMetronomo();
    await flush();
    service.alternarMetronomo();
    expect(metronome.alternarCalls).toBe(2);
  });

  describe('sessão livre (Fase 3e-3, sem treino/exercícios)', () => {
    it('iniciarLivre() vai direto pra running - sem count-in, sem exercicios', () => {
      service.iniciarLivre();
      expect(service.estado()).toBe('running');
      expect(service.exercicios()).toEqual([]);
      expect(metronome.tocarCountInCalls).toEqual([]);
    });

    it('cronometro conta indefinidamente - nunca vira timeUp (nao ha alvo de duracao)', async () => {
      service.iniciarLivre();
      await flush(30_000);
      expect(service.estado()).toBe('running');
      expect(service.segundosDecorridos()).toBe(30);
      expect(metronome.avisoTempoCumpridoCalls).toBe(0);
    });

    it('finalizarLivre() encerra e vai pra revisao, sem logs de exercicio', async () => {
      service.iniciarLivre();
      await flush(12_000);

      service.finalizarLivre();

      expect(service.estado()).toBe('reviewing');
      expect(service.totalSegundos()).toBe(12);
      expect(service.logs()).toEqual([]);
      expect(metronome.pararCalls).toBeGreaterThan(0);
    });

    it('finalizarLivre() e no-op fora de running (ex: ja em reviewing)', async () => {
      service.iniciarLivre();
      await flush(5_000);
      service.finalizarLivre();
      expect(service.estado()).toBe('reviewing');

      service.finalizarLivre();
      expect(service.estado()).toBe('reviewing');
      expect(service.totalSegundos()).toBe(5);
    });

    it('buildExecutionRequest com trainingId nulo - sessao sem treino vinculado', async () => {
      service.iniciarLivre();
      await flush(8_000);
      service.finalizarLivre();
      service.feeling.set('Bom');
      service.generalNotes.set('groove novo de shuffle');

      const request = service.buildExecutionRequest(null, 7);
      expect(request.trainingId).toBeNull();
      expect(request.goalId).toBe(7);
      expect(request.logs).toEqual([]);
      expect(request.feeling).toBe('Bom');
      expect(request.generalNotes).toBe('groove novo de shuffle');
    });
  });
});
