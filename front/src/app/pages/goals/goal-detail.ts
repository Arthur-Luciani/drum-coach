import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiService } from '../../api';
import {
  CreateExerciseRequest,
  CreateTrainingRequest,
  DrumPattern,
  Exercise,
  ExerciseKind,
  GoalDetail,
} from '../../models';
import { Badge } from '../../shared/badge';
import { formatDateBr, formatDurationLabel } from '../../shared/format';
import { goalStatusLabel } from '../../shared/labels';
import { PatternEditorComponent } from '../../shared/pattern-editor';
import { emptyPattern } from '../../shared/pattern-presets';
import { ProgressBar } from '../../shared/progress-bar';
import { ExerciseEditorComponent } from './exercise-editor';

@Component({
  selector: 'app-goal-detail',
  imports: [FormsModule, RouterLink, ProgressBar, Badge, PatternEditorComponent, ExerciseEditorComponent],
  templateUrl: './goal-detail.html',
})
export class GoalDetailPage {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);

  protected readonly goalId = Number(this.route.snapshot.paramMap.get('id'));
  protected readonly formatDateBr = formatDateBr;
  protected readonly formatDurationLabel = formatDurationLabel;
  protected readonly goalStatusLabel = goalStatusLabel;

  /**
   * Decisao de composicao: o mockup GoalDetail mostra o primeiro treino ja expandido
   * (com os exercicios visiveis) e os demais colapsados. Pra bater com isso no
   * primeiro carregamento real, expandimos o primeiro treino automaticamente uma unica
   * vez (nao mexe se o usuario ja tiver expandido/colapsado outro depois).
   */
  private autoExpandDone = false;

  protected readonly detail = signal<GoalDetail | null>(null);
  protected readonly notFound = signal(false);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly expandedTrainingId = signal<number | null>(null);
  protected readonly exercisesByTraining = signal<Record<number, Exercise[]>>({});
  protected readonly exercisesLoading = signal(false);

  /** Id do exercicio com o painel de edicao (`<app-exercise-editor>`) aberto - so um por
   * vez. `null` = nenhum aberto. */
  protected readonly editingExerciseId = signal<number | null>(null);

  // Novo treino
  protected newTrainingName = '';
  protected newTrainingDescription = '';
  protected newTrainingDuration = 60;
  protected newTrainingTargetReps: number | null = null;

  // Novo exercício (para o treino atualmente expandido)
  protected newExerciseName = '';
  /** Obrigatorio (ADR-0011) - `null` ate o usuario escolher no segmented control. */
  protected newExerciseKind: ExerciseKind | null = null;
  protected newExerciseType = '';
  protected newExerciseHowTo = '';
  /** Padrao em edicao pro exercicio `TOCA_JUNTO` novo - comeca vazio, o usuario pode
   * partir de um preset dentro do `<app-pattern-editor>`. Ignorado se `kind` for
   * `TRANSCRICAO`. */
  protected newExercisePattern: DrumPattern = emptyPattern();
  protected newExerciseTargetBpm: number | null = null;
  protected newExerciseTargetDurationSeconds: number | null = null;
  protected newExerciseVideoUrl = '';

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getGoal(this.goalId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.loading.set(false);
        if (!this.autoExpandDone) {
          this.autoExpandDone = true;
          const first = detail.trainings[0];
          if (first) {
            this.toggleExpand(first.trainingId);
          }
        }
      },
      error: (err) => {
        if (err?.status === 404) {
          this.notFound.set(true);
        } else {
          this.error.set('Falha ao carregar meta: ' + (err?.message ?? err));
        }
        this.loading.set(false);
      },
    });
  }

  protected focusGoal(): void {
    this.api.focusGoal(this.goalId).subscribe({
      next: () => this.reload(),
      error: (err) => {
        this.error.set('Falha ao marcar meta como foco: ' + (err?.message ?? err));
      },
    });
  }

  protected createTraining(): void {
    const name = this.newTrainingName.trim();
    if (!name) {
      return;
    }
    const request: CreateTrainingRequest = {
      goalId: this.goalId,
      name,
      description: this.newTrainingDescription.trim() || null,
      targetDurationMinutes: this.newTrainingDuration,
      targetRepetitions: this.newTrainingTargetReps,
      orderIndex: this.detail()?.trainings.length ?? 0,
    };
    this.api.createTraining(request).subscribe({
      next: () => {
        this.newTrainingName = '';
        this.newTrainingDescription = '';
        this.newTrainingDuration = 60;
        this.newTrainingTargetReps = null;
        this.reload();
      },
      error: (err) => {
        this.error.set('Falha ao criar treino: ' + (err?.message ?? err));
      },
    });
  }

  protected toggleExpand(trainingId: number): void {
    if (this.expandedTrainingId() === trainingId) {
      this.expandedTrainingId.set(null);
      return;
    }
    this.expandedTrainingId.set(trainingId);
    this.resetExerciseForm();
    if (!this.exercisesByTraining()[trainingId]) {
      this.loadExercises(trainingId);
    }
  }

  private loadExercises(trainingId: number): void {
    this.exercisesLoading.set(true);
    this.api.listExercises(trainingId).subscribe({
      next: (exercises) => {
        this.exercisesByTraining.update((map) => ({ ...map, [trainingId]: exercises }));
        this.exercisesLoading.set(false);
      },
      error: (err) => {
        this.error.set('Falha ao carregar exercícios: ' + (err?.message ?? err));
        this.exercisesLoading.set(false);
      },
    });
  }

  protected exercisesFor(trainingId: number): Exercise[] {
    return this.exercisesByTraining()[trainingId] ?? [];
  }

  protected createExercise(trainingId: number): void {
    const name = this.newExerciseName.trim();
    const kind = this.newExerciseKind;
    if (!name || !kind) {
      return;
    }
    const videoUrl = this.newExerciseVideoUrl.trim();
    const request: CreateExerciseRequest = {
      name,
      kind,
      exerciseType: this.newExerciseType.trim() || null,
      howToExecute: this.newExerciseHowTo.trim() || null,
      pattern: kind === 'TOCA_JUNTO' ? this.newExercisePattern : null,
      targetBpm: this.newExerciseTargetBpm,
      targetDurationSeconds: this.newExerciseTargetDurationSeconds,
      videoSourceType: videoUrl ? 'LINK' : null,
      videoUrl: videoUrl || null,
      videoFilePath: null,
      orderIndex: this.exercisesFor(trainingId).length,
    };
    this.api.createExercise(trainingId, request).subscribe({
      next: () => {
        this.resetExerciseForm();
        this.loadExercises(trainingId);
      },
      error: (err) => {
        this.error.set('Falha ao criar exercício: ' + (err?.message ?? err));
      },
    });
  }

  /** Abre/fecha o painel de edicao inline de um exercicio existente. */
  protected toggleEditExercise(exerciseId: number): void {
    this.editingExerciseId.update((id) => (id === exerciseId ? null : exerciseId));
  }

  /** Gravacao no `<app-exercise-editor>` deu certo - recarrega a lista pra refletir os
   * dados novos (mantem o painel aberto, com a copia de trabalho re-sincronizada). */
  protected onExerciseSaved(trainingId: number): void {
    this.loadExercises(trainingId);
  }

  private resetExerciseForm(): void {
    this.newExerciseName = '';
    this.newExerciseKind = null;
    this.newExerciseType = '';
    this.newExerciseHowTo = '';
    this.newExercisePattern = emptyPattern();
    this.newExerciseTargetBpm = null;
    this.newExerciseTargetDurationSeconds = null;
    this.newExerciseVideoUrl = '';
  }
}
