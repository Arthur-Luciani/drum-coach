import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../api';
import {
  CreateExecutionRequest,
  Exercise,
  ExecutionExerciseLogRequest,
  Execution,
  Goal,
  Training,
} from '../../models';
import { Badge } from '../../shared/badge';
import { formatDateShort } from '../../shared/format';

interface ExerciseLogInput {
  exerciseId: number;
  name: string;
  targetBpm: number | null;
  achievedBpm: number | null;
  actualDurationSeconds: number | null;
  notes: string;
}

type FilterMode = 'none' | 'training' | 'goal';

@Component({
  selector: 'app-executions',
  imports: [FormsModule, Badge],
  templateUrl: './executions.html',
})
export class Executions {
  private readonly api = inject(ApiService);

  protected readonly formatDateShort = formatDateShort;

  protected readonly trainings = signal<Training[]>([]);
  protected readonly goals = signal<Goal[]>([]);
  protected readonly executions = signal<Execution[]>([]);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly trainingNameById = computed(() => {
    const map = new Map<number, string>();
    for (const t of this.trainings()) {
      map.set(t.id, t.name);
    }
    return map;
  });

  protected readonly goalTitleById = computed(() => {
    const map = new Map<number, string>();
    for (const g of this.goals()) {
      map.set(g.id, g.title);
    }
    return map;
  });

  protected readonly trainingsForSelect = computed(() =>
    this.trainings().map((t) => {
      const goalTitle = t.goalId != null ? (this.goalTitleById().get(t.goalId) ?? `meta #${t.goalId}`) : null;
      return { training: t, label: t.name + (goalTitle ? ` (${goalTitle})` : ' (avulso)') };
    }),
  );

  // Filtro da listagem
  protected filterMode: FilterMode = 'none';
  protected filterId: number | null = null;

  // Formulário de nova execução
  protected selectedTrainingId: number | null = null;
  protected executionDate = new Date().toISOString().slice(0, 10);
  protected actualDurationMinutes: number | null = null;
  protected feeling = '';
  protected generalNotes = '';
  protected goalId: number | null = null;
  protected exerciseLogs: ExerciseLogInput[] = [];
  protected exercisesLoading = false;

  constructor() {
    this.reloadReferenceData();
    this.reloadExecutions();
  }

  private reloadReferenceData(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      trainings: this.api.listTrainings(),
      goals: this.api.listGoals(),
    }).subscribe({
      next: ({ trainings, goals }) => {
        this.trainings.set(trainings);
        this.goals.set(goals);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Falha ao carregar dados de apoio: ' + (err?.message ?? err));
        this.loading.set(false);
      },
    });
  }

  protected reloadExecutions(): void {
    const filter =
      this.filterMode === 'training' && this.filterId != null
        ? { trainingId: this.filterId }
        : this.filterMode === 'goal' && this.filterId != null
          ? { goalId: this.filterId }
          : undefined;
    this.api.listExecutions(filter).subscribe({
      next: (executions) => {
        const sorted = [...executions].sort((a, b) => (a.executionDate < b.executionDate ? 1 : -1));
        this.executions.set(sorted);
      },
      error: (err) => {
        this.error.set('Falha ao carregar execuções: ' + (err?.message ?? err));
      },
    });
  }

  protected onFilterModeChange(): void {
    this.filterId = null;
    this.reloadExecutions();
  }

  protected onTrainingSelected(): void {
    this.exerciseLogs = [];
    if (this.selectedTrainingId == null) {
      return;
    }
    this.exercisesLoading = true;
    this.api.listExercises(this.selectedTrainingId).subscribe({
      next: (exercises: Exercise[]) => {
        this.exerciseLogs = exercises.map((e) => ({
          exerciseId: e.id,
          name: e.name,
          targetBpm: e.targetBpm,
          achievedBpm: null,
          actualDurationSeconds: null,
          notes: '',
        }));
        this.exercisesLoading = false;
      },
      error: (err) => {
        this.error.set('Falha ao carregar exercícios do treino: ' + (err?.message ?? err));
        this.exercisesLoading = false;
      },
    });
  }

  protected createExecution(): void {
    if (this.selectedTrainingId == null) {
      return;
    }
    const logs: ExecutionExerciseLogRequest[] = this.exerciseLogs
      .filter((l) => l.achievedBpm != null || l.actualDurationSeconds != null || l.notes.trim())
      .map((l) => ({
        exerciseId: l.exerciseId,
        achievedBpm: l.achievedBpm,
        actualDurationSeconds: l.actualDurationSeconds,
        notes: l.notes.trim() || null,
      }));

    const request: CreateExecutionRequest = {
      trainingId: this.selectedTrainingId,
      executionDate: this.executionDate,
      actualDurationMinutes: this.actualDurationMinutes,
      feeling: this.feeling.trim() || null,
      generalNotes: this.generalNotes.trim() || null,
      goalId: this.goalId,
      logs,
    };
    this.api.createExecution(request).subscribe({
      next: () => {
        this.resetForm();
        this.reloadExecutions();
      },
      error: (err) => {
        this.error.set('Falha ao registrar execução: ' + (err?.message ?? err));
      },
    });
  }

  /** Rótulo de treino de uma execução pra exibição - `trainingId` nulo é uma sessão
   * livre (Fase 3e-3, sem treino vinculado), não um id ausente por erro. */
  protected trainingLabel(execution: Execution): string {
    if (execution.trainingId == null) {
      return 'Sessão livre';
    }
    return this.trainingNameById().get(execution.trainingId) ?? `#${execution.trainingId}`;
  }

  private resetForm(): void {
    this.selectedTrainingId = null;
    this.executionDate = new Date().toISOString().slice(0, 10);
    this.actualDurationMinutes = null;
    this.feeling = '';
    this.generalNotes = '';
    this.goalId = null;
    this.exerciseLogs = [];
  }
}
