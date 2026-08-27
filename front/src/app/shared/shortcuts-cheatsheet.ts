import { Component, effect, inject } from '@angular/core';

import { KeyboardShortcutsService } from '../core/keyboard-shortcuts.service';
import { ShortcutsOverlayService } from '../core/shortcuts-overlay.service';
import { KeyCap } from './key-cap';

/**
 * Uma secao do cheat-sheet (ex: "Navegacao", e nas fases futuras "Metronomo", "Modo
 * Sessao"). O componente itera sobre uma lista de secoes (`@for`) em vez de ter markup
 * fixo pra uma unica secao, entao adicionar uma secao nova nas proximas fases e so
 * acrescentar um item aqui.
 */
export interface CheatsheetSection {
  title: string;
  items: { key: string; description: string }[];
}

/**
 * Overlay global "quais atalhos existem" (`?`). Escuta `ShortcutsOverlayService.isOpen`
 * e, enquanto aberto, registra seu proprio scope de teclado no topo da pilha do
 * `KeyboardShortcutsService` - com `blockFallthrough` no padrao (`true`), entao nenhuma
 * tecla vaza pra navegacao global enquanto o overlay esta na tela. `?` e `Escape` fecham.
 *
 * Vive montado permanentemente em `app.html` (fora do `router-outlet`), controlado
 * inteiramente pelo signal do servico - nunca desmonta/remonta por navegacao.
 */
@Component({
  selector: 'app-shortcuts-cheatsheet',
  imports: [KeyCap],
  templateUrl: './shortcuts-cheatsheet.html',
  styleUrl: './shortcuts-cheatsheet.css',
})
export class ShortcutsCheatsheet {
  private readonly shortcuts = inject(KeyboardShortcutsService);
  protected readonly overlay = inject(ShortcutsOverlayService);

  protected readonly sections: CheatsheetSection[] = [
    {
      title: 'Navegação',
      items: [
        { key: '1', description: 'Início' },
        { key: '2', description: 'Execuções' },
        { key: '3', description: 'Aulas' },
        { key: '4', description: 'Repertório' },
        { key: '5', description: 'Metas' },
        { key: '?', description: 'Abrir/fechar esta lista de atalhos' },
      ],
    },
    {
      title: 'Metrônomo',
      items: [
        { key: 'M', description: 'Abrir/fechar o metrônomo avulso' },
        { key: 'Espaço', description: 'Play/pause (dentro do metrônomo)' },
        { key: 'Esc', description: 'Fechar o metrônomo' },
      ],
    },
    {
      title: 'Escolher treino (Dashboard, com meta em foco)',
      items: [
        { key: 'Enter', description: 'Abrir o seletor de treino' },
        { key: '1–9', description: 'Escolher e confirmar direto um item' },
        { key: '↑ ↓', description: 'Navegar entre os itens' },
        { key: 'Enter', description: 'Confirmar o item destacado (dentro do seletor)' },
        { key: 'Esc', description: 'Fechar o seletor sem escolher' },
      ],
    },
  ];

  constructor() {
    effect((onCleanup) => {
      if (!this.overlay.isOpen()) {
        return;
      }
      const unregister = this.shortcuts.register({
        handlers: {
          '?': () => this.overlay.close(),
          escape: () => this.overlay.close(),
        },
      });
      onCleanup(unregister);
    });
  }

  protected close(): void {
    this.overlay.close();
  }
}
