import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { KeyboardShortcutsService } from './core/keyboard-shortcuts.service';
import { MetronomeOverlayService } from './core/metronome-overlay.service';
import { ShortcutsOverlayService } from './core/shortcuts-overlay.service';
import { TrainingPickerOverlayService } from './core/training-picker-overlay.service';
import { KeyCap } from './shared/key-cap';
import { MetronomeOverlay } from './shared/metronome-overlay';
import { ShortcutsCheatsheet } from './shared/shortcuts-cheatsheet';
import { TrainingPicker } from './shared/training-picker';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, KeyCap, ShortcutsCheatsheet, MetronomeOverlay, TrainingPicker],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly shortcutsOverlay = inject(ShortcutsOverlayService);
  protected readonly metronomeOverlay = inject(MetronomeOverlayService);

  private readonly router = inject(Router);
  private readonly shortcuts = inject(KeyboardShortcutsService);
  private readonly trainingPickerOverlay = inject(TrainingPickerOverlayService);

  /** `true` na rota `/session` - o Modo Sessao e uma tela imersiva (sem o cabecalho
   * global do app), pra sobrar `100vh` inteiros pra pauta e caber tudo sem scroll. */
  protected readonly immersive = signal(this.isImmersiveRoute());

  constructor() {
    this.router.events
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.immersive.set(this.isImmersiveRoute()));

    // Escopo global de navegacao - base da pilha de atalhos, registrado uma vez e nunca
    // desregistrado (vive pra sempre, junto com o app-root). `blockFallthrough: false`
    // porque e a base: nao ha nada abaixo dele na pilha pra bloquear.
    this.shortcuts.register({
      blockFallthrough: false,
      handlers: {
        '1': () => void this.router.navigate(['/dashboard']),
        '2': () => void this.router.navigate(['/executions']),
        '3': () => void this.router.navigate(['/lessons']),
        '4': () => void this.router.navigate(['/repertoire']),
        '5': () => void this.router.navigate(['/goals']),
        '?': () => this.shortcutsOverlay.open(),
        m: () => this.metronomeOverlay.toggle(),
        // So abre o TrainingPicker a partir do Dashboard, e so quando ha uma meta em
        // foco carregada (`open()` do overlay ja e no-op sem contexto, mas checar aqui
        // evita interceptar `Enter` em outras telas onde ele nao tem nenhum sentido).
        enter: () => {
          if (this.router.url === '/dashboard') {
            this.trainingPickerOverlay.open();
          }
        },
      },
    });
  }

  private isImmersiveRoute(): boolean {
    return this.router.url.split(/[?#]/)[0].startsWith('/session');
  }
}
