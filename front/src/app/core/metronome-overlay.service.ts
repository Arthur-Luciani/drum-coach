import { Injectable, signal } from '@angular/core';

/**
 * Estado de visibilidade do overlay de metronomo avulso (`Metronome`, tecla `M`).
 * Servico simples e singleton (mesmo padrao de `ShortcutsOverlayService`, Fase 3b) pra
 * que qualquer componente futuro possa abrir o overlay sem depender do componente raiz.
 */
@Injectable({ providedIn: 'root' })
export class MetronomeOverlayService {
  private readonly openSignal = signal(false);
  readonly isOpen = this.openSignal.asReadonly();

  open(): void {
    this.openSignal.set(true);
  }

  close(): void {
    this.openSignal.set(false);
  }

  toggle(): void {
    this.openSignal.update((v) => !v);
  }
}
