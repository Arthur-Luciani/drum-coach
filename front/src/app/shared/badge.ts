import { Component, input } from '@angular/core';

/**
 * Chip de status/origem (estilo Woodshed) - so um `<span class="badge">` com a
 * variante certa aplicada via classe CSS (`.badge-<variant>`, ver `styles.css`).
 * Centraliza a marcacao repetida em Goals/GoalDetail/Repertoire/Executions - a cor de
 * cada variante fica so em `styles.css`, nunca duplicada por tela.
 */
@Component({
  selector: 'app-badge',
  template: `<span class="badge" [class]="'badge-' + variant().toLowerCase()">{{ label() }}</span>`,
})
export class Badge {
  /** Sufixo da classe CSS, ex: `IN_PROGRESS` -> `.badge-in_progress`. */
  readonly variant = input.required<string>();
  readonly label = input.required<string>();
}
