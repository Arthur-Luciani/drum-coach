import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, switchMap } from 'rxjs';

import { ApiService } from '../../api';
import { TrainingPickerOverlayService } from '../../core/training-picker-overlay.service';
import { Execution, Goal, GoalDetail, Lesson, Training } from '../../models';
import { Badge } from '../../shared/badge';
import { formatDateShort, formatDurationMinutesLabel } from '../../shared/format';
import { KeyCap } from '../../shared/key-cap';
import { goalStatusLabel } from '../../shared/labels';
import { ProgressBar } from '../../shared/progress-bar';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, ProgressBar, Badge, KeyCap],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class Dashboard {
  private readonly api = inject(ApiService);
  private readonly trainingPickerOverlay = inject(TrainingPickerOverlayService);

  protected readonly formatDateShort = formatDateShort;
  protected readonly formatDurationMinutesLabel = formatDurationMinutesLabel;
  protected readonly goalStatusLabel = goalStatusLabel;

  protected readonly focusedGoalDetail = signal<GoalDetail | null>(null);
  protected readonly goalsInProgress = signal<Goal[]>([]);
  protected readonly recentExecutions = signal<Execution[]>([]);
  protected readonly recentLessons = signal<Lesson[]>([]);
  protected readonly trainingNameById = signal<Map<number, string>>(new Map());

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  /**
   * "Outras metas em andamento" (card secundario) nunca repete a meta ja destacada na
   * hero - `goalsInProgress` traz TODAS as metas em andamento, incluindo a focada.
   */
  protected readonly otherGoalsInProgress = computed(() => {
    const focusedId = this.focusedGoalDetail()?.goal.id;
    return this.goalsInProgress().filter((g) => g.id !== focusedId);
  });

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      goals: this.api.listGoals(),
      executions: this.api.listExecutions(),
      lessons: this.api.listLessons(),
      trainings: this.api.listTrainings(),
    })
      .pipe(
        switchMap(({ goals, executions, lessons, trainings }) => {
          const focusedGoal = goals.find((g: Goal) => g.inFocus) ?? null;
          const detail$ = focusedGoal != null ? this.api.getGoal(focusedGoal.id) : of<GoalDetail | null>(null);
          return detail$.pipe(map((detail) => ({ detail, goals, executions, lessons, trainings })));
        }),
      )
      .subscribe({
        next: ({ detail, goals, executions, lessons, trainings }) => {
          this.focusedGoalDetail.set(detail);
          this.goalsInProgress.set(
            goals.filter((g: Goal) => g.status === 'NOT_STARTED' || g.status === 'IN_PROGRESS'),
          );
          this.recentExecutions.set(
            [...executions].sort((a, b) => (a.executionDate < b.executionDate ? 1 : -1)).slice(0, 5),
          );
          this.recentLessons.set(
            [...lessons].sort((a, b) => (a.lessonDate < b.lessonDate ? 1 : -1)).slice(0, 3),
          );

          const nameMap = new Map<number, string>();
          const durationMap = new Map<number, number>();
          for (const t of trainings as Training[]) {
            nameMap.set(t.id, t.name);
            durationMap.set(t.id, t.targetDurationMinutes);
          }
          this.trainingNameById.set(nameMap);

          // Publica o contexto (meta em foco + duracao alvo dos treinos) pro
          // TrainingPicker reaproveitar - ver TrainingPickerOverlayService.
          this.trainingPickerOverlay.setContext(detail, durationMap);

          this.loading.set(false);
        },
        error: (err) => {
          this.error.set('Falha ao carregar o painel: ' + (err?.message ?? err));
          this.loading.set(false);
        },
      });
  }

  protected openTrainingPicker(): void {
    this.trainingPickerOverlay.open();
  }

  /** Rótulo de treino de uma execução pra exibição - `trainingId` nulo é uma sessão
   * livre (Fase 3e-3, sem treino vinculado), não um id ausente por erro. */
  protected trainingLabel(execution: Execution): string {
    if (execution.trainingId == null) {
      return 'Sessão livre';
    }
    return this.trainingNameById().get(execution.trainingId) ?? `#${execution.trainingId}`;
  }
}
