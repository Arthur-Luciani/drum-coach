import { Component, computed, effect, inject } from '@angular/core';

import { KeyboardShortcutsService } from '../core/keyboard-shortcuts.service';
import { MetronomeOverlayService } from '../core/metronome-overlay.service';
import { MetronomeService, Subdivisao } from '../core/metronome.service';
import { KeyCap } from './key-cap';

const BPM_MIN = 30;
const BPM_MAX = 300;

/**
 * Overlay de metronomo avulso (tecla `M`). Utilitario rapido de aquecimento - NAO
 * registra nada (isso e trabalho de fase futura, o Modo Sessao). So exibe/controla o
 * `MetronomeService` singleton (BPM, compasso, subdivisao, acento, tap tempo).
 *
 * Segue o mesmo padrao do `ShortcutsCheatsheet` (Fase 3b): visibilidade controlada por
 * um servico proprio (`MetronomeOverlayService`), e enquanto aberto registra seu proprio
 * scope no topo da pilha do `KeyboardShortcutsService` (`blockFallthrough` no padrao
 * `true`) com `Escape` (fecha o overlay - nao para o metronomo, so esconde o painel) e
 * `Espaco` (play/pause). Vive montado permanentemente em `app.html`, fora do
 * `router-outlet`.
 */
@Component({
  selector: 'app-metronome-overlay',
  imports: [KeyCap],
  templateUrl: './metronome-overlay.html',
  styleUrl: './metronome-overlay.css',
})
export class MetronomeOverlay {
  private readonly shortcuts = inject(KeyboardShortcutsService);
  protected readonly overlay = inject(MetronomeOverlayService);
  protected readonly metronome = inject(MetronomeService);

  protected readonly subdivisions: { value: Subdivisao; label: string; title: string }[] = [
    { value: 'quarter', label: '♩', title: 'Semínima - 1 clique por tempo' },
    { value: 'eighth', label: '♫', title: 'Colcheia - 2 cliques por tempo' },
    { value: 'triplet', label: '3', title: 'Tercina - 3 cliques por tempo' },
    { value: 'sixteenth', label: '♬', title: 'Semicolcheia - 4 cliques por tempo' },
  ];

  protected readonly compassoOptions = [2, 3, 4, 5, 6, 7, 8, 9];

  /** Um item por tempo do compasso atual - alimenta os pontinhos de pulso visual. */
  protected readonly beatDots = computed(() =>
    Array.from({ length: this.metronome.compasso() }, (_, i) => i),
  );

  constructor() {
    effect((onCleanup) => {
      if (!this.overlay.isOpen()) {
        return;
      }
      const unregister = this.shortcuts.register({
        handlers: {
          escape: () => this.overlay.close(),
          ' ': () => this.metronome.alternar(),
        },
      });
      onCleanup(unregister);
    });
  }

  protected close(): void {
    this.overlay.close();
  }

  protected adjustBpm(delta: number): void {
    this.metronome.bpm.update((v) => this.clampBpm(v + delta));
  }

  protected onBpmInput(event: Event): void {
    const raw = Number((event.target as HTMLInputElement).value);
    if (Number.isFinite(raw)) {
      this.metronome.bpm.set(this.clampBpm(raw));
    }
  }

  protected onCompassoChange(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    if (Number.isFinite(value) && value > 0) {
      this.metronome.compasso.set(value);
    }
  }

  protected selectSubdivisao(value: Subdivisao): void {
    this.metronome.subdivisao.set(value);
  }

  protected toggleAcento(): void {
    this.metronome.acentuarPrimeiroTempo.update((v) => !v);
  }

  private clampBpm(value: number): number {
    return Math.min(BPM_MAX, Math.max(BPM_MIN, Math.round(value)));
  }
}
