import { Injectable, signal } from '@angular/core';

/**
 * Estado de visibilidade do overlay de cheat-sheet de atalhos (`ShortcutsCheatsheet`).
 * Servico simples e singleton em vez de estado local do `App` pra que qualquer
 * componente futuro (ex. um botao de ajuda numa tela especifica) possa abrir o overlay
 * sem depender do componente raiz.
 */
@Injectable({ providedIn: 'root' })
export class ShortcutsOverlayService {
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
