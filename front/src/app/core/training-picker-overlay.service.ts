import { Injectable, signal } from '@angular/core';

import { GoalDetail } from '../models';

/**
 * Estado de visibilidade + contexto do overlay `TrainingPicker` (tecla `Enter` no
 * Dashboard). Mesmo padrao "isOpen signal" de `ShortcutsOverlayService`/
 * `MetronomeOverlayService` (Fases 3b/3c), com um adicional: como o picker lista os
 * treinos da meta em foco (`GoalDetail`) que o Dashboard ja busca pra montar a hero, o
 * servico guarda essa referencia (`setContext`) em vez de o picker refazer a chamada de
 * API. O Dashboard chama `setContext` sempre que recarrega os dados (independente do
 * overlay estar aberto) - assim o contexto fica sempre atualizado, e `open()` (chamado
 * pelo atalho global `Enter` em `app.ts` ou pelo botao "Escolher treino") so abre de
 * fato quando ha uma meta em foco carregada.
 *
 * A rota stub `/session` (Fase 3d) tambem le `goalDetail()` daqui pra resolver o nome do
 * treino escolhido sem uma nova chamada de API (ver `pages/session/session.ts`).
 */
@Injectable({ providedIn: 'root' })
export class TrainingPickerOverlayService {
  private readonly openSignal = signal(false);
  readonly isOpen = this.openSignal.asReadonly();

  private readonly goalDetailSignal = signal<GoalDetail | null>(null);
  readonly goalDetail = this.goalDetailSignal.asReadonly();

  private readonly targetDurationMinutesByTrainingIdSignal = signal<Map<number, number>>(new Map());
  readonly targetDurationMinutesByTrainingId = this.targetDurationMinutesByTrainingIdSignal.asReadonly();

  /** Chamado pelo Dashboard a cada `reload()` - mantem o contexto em dia sem reabrir nada. */
  setContext(goalDetail: GoalDetail | null, targetDurationMinutesByTrainingId: Map<number, number>): void {
    this.goalDetailSignal.set(goalDetail);
    this.targetDurationMinutesByTrainingIdSignal.set(targetDurationMinutesByTrainingId);
  }

  /** No-op se nao houver meta em foco carregada - `Enter` sem meta em foco nao faz nada. */
  open(): void {
    if (this.goalDetailSignal() == null) {
      return;
    }
    this.openSignal.set(true);
  }

  close(): void {
    this.openSignal.set(false);
  }
}
