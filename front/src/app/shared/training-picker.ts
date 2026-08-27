import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { KeyboardShortcutsService } from '../core/keyboard-shortcuts.service';
import { TrainingPickerOverlayService } from '../core/training-picker-overlay.service';
import { formatDurationMinutesLabel } from './format';
import { KeyCap } from './key-cap';
import { ProgressBar } from './progress-bar';

interface TrainingPickerTrainingItem {
  kind: 'training';
  trainingId: number;
  name: string;
  targetDurationMinutes: number | null;
  targetRepetitions: number | null;
  completedCount: number;
}

interface TrainingPickerFreeItem {
  kind: 'free';
}

type TrainingPickerItem = TrainingPickerTrainingItem | TrainingPickerFreeItem;

/**
 * Overlay `TrainingPicker` (tecla `Enter` no Dashboard, so quando ha uma meta em foco -
 * ver `TrainingPickerOverlayService.open()`). Lista os treinos da meta em foco (dados ja
 * carregados pelo Dashboard, via `TrainingPickerOverlayService.goalDetail()` - nenhuma
 * chamada de API nova aqui) numerados, mais uma ultima opcao "Sessao livre — sem
 * treino". Confirmar navega pra `/session` (com `trainingId` na query string, ou sem
 * parametro nenhum pra sessao livre) - a rota `/session` em si e so um stub nesta fase
 * (Fase 3d); o Modo Sessao de verdade e a Fase 3e.
 *
 * Mesmo padrao dos demais overlays (`MetronomeOverlay`, `ShortcutsCheatsheet`): enquanto
 * aberto, registra seu proprio scope no topo da pilha do `KeyboardShortcutsService`
 * (`blockFallthrough` no padrao `true`). Alem de `escape`/`enter`, tambem trata digitos
 * `1`-`9` (selecionam E confirmam direto) e `ArrowUp`/`ArrowDown` (movem um cursor de
 * selecao, destacado visualmente - `enter` confirma o item sob o cursor).
 */
@Component({
  selector: 'app-training-picker',
  imports: [KeyCap, ProgressBar],
  templateUrl: './training-picker.html',
  styleUrl: './training-picker.css',
})
export class TrainingPicker {
  private readonly shortcuts = inject(KeyboardShortcutsService);
  private readonly router = inject(Router);
  protected readonly overlay = inject(TrainingPickerOverlayService);

  protected readonly formatDurationMinutesLabel = formatDurationMinutesLabel;

  private readonly cursorSignal = signal(0);
  protected readonly cursor = this.cursorSignal.asReadonly();

  protected readonly items = computed<TrainingPickerItem[]>(() => {
    const detail = this.overlay.goalDetail();
    const durations = this.overlay.targetDurationMinutesByTrainingId();
    const trainingItems: TrainingPickerItem[] = (detail?.trainings ?? []).map((t) => ({
      kind: 'training',
      trainingId: t.trainingId,
      name: t.name,
      targetDurationMinutes: durations.get(t.trainingId) ?? null,
      targetRepetitions: t.targetRepetitions,
      completedCount: t.completedCount,
    }));
    return [...trainingItems, { kind: 'free' }];
  });

  /** Rotulo do KeyCap de "escolher" no rodape - '1' se so houver a opcao "sessao livre", '1–N' caso contrario. */
  protected readonly digitsHintLabel = computed(() => {
    const count = this.items().length;
    return count > 1 ? `1–${count}` : '1';
  });

  constructor() {
    // Escopo de teclado so existe enquanto o overlay esta aberto - registrado/desregistrado
    // via effect(onCleanup), mesmo padrao de MetronomeOverlay/ShortcutsCheatsheet.
    effect((onCleanup) => {
      if (!this.overlay.isOpen()) {
        return;
      }
      this.cursorSignal.set(0);

      const handlers: Record<string, (event: KeyboardEvent) => void> = {
        escape: () => this.overlay.close(),
        enter: () => this.confirm(this.cursorSignal()),
        arrowup: () => this.moveCursor(-1),
        arrowdown: () => this.moveCursor(1),
      };
      for (let i = 1; i <= 9; i++) {
        handlers[String(i)] = () => this.confirm(i - 1);
      }

      const unregister = this.shortcuts.register({ handlers });
      onCleanup(unregister);
    });
  }

  protected close(): void {
    this.overlay.close();
  }

  protected setCursor(index: number): void {
    this.cursorSignal.set(index);
  }

  private moveCursor(delta: number): void {
    const max = this.items().length - 1;
    this.cursorSignal.update((v) => Math.min(max, Math.max(0, v + delta)));
  }

  protected confirm(index: number): void {
    const item = this.items()[index];
    if (!item) {
      return;
    }
    this.overlay.close();
    if (item.kind === 'training') {
      void this.router.navigate(['/session'], { queryParams: { trainingId: item.trainingId } });
    } else {
      void this.router.navigate(['/session']);
    }
  }
}
