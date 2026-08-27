import { Component, input } from '@angular/core';

/**
 * Chip visual de "tecla" (estilo Woodshed) - usado pra mostrar atalhos de teclado, ex. na
 * legenda do Modo Sessao ou no cheat-sheet de atalhos (`?`). So exibe o label recebido,
 * sem logica de captura de teclado - isso fica no KeyboardShortcutsService (fase 3b).
 */
@Component({
  selector: 'app-key-cap',
  template: `<kbd class="key-cap">{{ label() }}</kbd>`,
  styles: `
    .key-cap {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 1.6rem;
      padding: 0.15rem 0.45rem;
      font-family: var(--font-mono);
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.4;
      color: var(--color-text);
      background: var(--color-surface-strong);
      border: 1px solid var(--color-border-strong);
      border-bottom-width: 2px;
      border-radius: 5px;
      box-shadow: 0 1px 0 var(--color-border-strong);
    }
  `,
})
export class KeyCap {
  readonly label = input.required<string>();
}
