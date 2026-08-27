import { Component, DestroyRef, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../../api';
import { DrumPattern, Exercise, MarkedPassage, MarkedPassageRequest } from '../../models';
import { formatClock } from '../../shared/format';
import { PatternEditorComponent } from '../../shared/pattern-editor';
import { clonePattern, emptyPattern } from '../../shared/pattern-presets';

/**
 * `ExerciseEditorComponent` (`<app-exercise-editor>`) - painel inline (expander) pra
 * editar um exercicio JA EXISTENTE, aberto sob a linha do exercicio na tela de meta
 * (ver ADR-0011, Fase 4d). `kind` e imutavel, entao o painel so mostra o que faz sentido
 * pro tipo:
 *
 * - `TOCA_JUNTO`: o `<app-pattern-editor>` + "salvar" -> `PATCH /api/exercises/{id}`
 *   com `{ pattern }`.
 * - `TRANSCRICAO`: textarea de notas (`howToExecute`) -> `PATCH` com `{ howToExecute }`,
 *   mais a lista de trechos marcados com adicionar (`POST .../passages`) e remover
 *   (`DELETE .../passages/{pid}`) - cada um efetivado na hora, sem "salvar" separado.
 *
 * Faz as chamadas HTTP direto (injeta `ApiService`); avisa o pai via `saved` pra ele
 * recarregar a lista, e `closed` pra fechar o expander.
 */
@Component({
  selector: 'app-exercise-editor',
  imports: [FormsModule, PatternEditorComponent],
  templateUrl: './exercise-editor.html',
  styleUrl: './exercise-editor.css',
})
export class ExerciseEditorComponent {
  private readonly api = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly exercise = input.required<Exercise>();
  /** Emitido apos qualquer gravacao bem-sucedida (pattern, notas, trecho add/remove). */
  readonly saved = output<void>();
  readonly closed = output<void>();

  protected readonly formatClock = formatClock;

  /** Copia de trabalho do padrao (TOCA_JUNTO) - o `<app-pattern-editor>` e controlado. */
  protected readonly working = signal<DrumPattern>(emptyPattern());
  /** Copia de trabalho da nota livre / notas de transcricao. */
  protected notes = '';
  /** Trechos marcados - espelha o servidor, atualizado a cada add/remove. */
  protected readonly passages = signal<MarkedPassage[]>([]);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  // Form de novo trecho (TRANSCRICAO).
  protected newFromSeconds: number | null = null;
  protected newToSeconds: number | null = null;
  protected newLabel = '';

  protected readonly isTocaJunto = computed(() => this.exercise().kind === 'TOCA_JUNTO');

  constructor() {
    // Reseta as copias de trabalho sempre que o exercicio de entrada muda (inclusive
    // apos um reload do pai que recria os objetos).
    effect(() => {
      const ex = this.exercise();
      this.working.set(ex.pattern ? clonePattern(ex.pattern) : emptyPattern());
      this.notes = ex.howToExecute ?? '';
      this.passages.set([...(ex.passages ?? [])]);
    });
  }

  protected onPattern(next: DrumPattern): void {
    this.working.set(next);
  }

  protected salvarPattern(): void {
    this.patch({ pattern: this.working() }, 'Falha ao salvar o padrão');
  }

  protected salvarNotas(): void {
    this.patch({ howToExecute: this.notes.trim() || null }, 'Falha ao salvar as notas');
  }

  private patch(req: Parameters<ApiService['updateExercise']>[1], errMsg: string): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    const sub = this.api.updateExercise(this.exercise().id, req).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.emit();
      },
      error: (err) => {
        this.saving.set(false);
        this.error.set(errMsg + ': ' + (err?.message ?? err));
      },
    });
    this.destroyRef.onDestroy(() => sub.unsubscribe());
  }

  protected adicionarTrecho(): void {
    if (this.newFromSeconds == null || this.newFromSeconds < 0) {
      return;
    }
    const req: MarkedPassageRequest = {
      fromSeconds: Math.floor(this.newFromSeconds),
      toSeconds: this.newToSeconds != null ? Math.floor(this.newToSeconds) : null,
      label: this.newLabel.trim() || null,
    };
    this.error.set(null);
    this.api.addPassage(this.exercise().id, req).subscribe({
      next: (passage) => {
        this.passages.update((list) => [...list, passage]);
        this.newFromSeconds = null;
        this.newToSeconds = null;
        this.newLabel = '';
        this.saved.emit();
      },
      error: (err) => this.error.set('Falha ao adicionar trecho: ' + (err?.message ?? err)),
    });
  }

  protected removerTrecho(id: number): void {
    this.error.set(null);
    this.api.deletePassage(this.exercise().id, id).subscribe({
      next: () => {
        this.passages.update((list) => list.filter((p) => p.id !== id));
        this.saved.emit();
      },
      error: (err) => this.error.set('Falha ao remover trecho: ' + (err?.message ?? err)),
    });
  }
}
