import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../api';
import { CreateGoalRequest, Goal, Training } from '../../models';
import { Badge } from '../../shared/badge';
import { formatDateBr } from '../../shared/format';
import { goalStatusLabel } from '../../shared/labels';

@Component({
  selector: 'app-goals',
  imports: [FormsModule, RouterLink, Badge],
  templateUrl: './goals.html',
})
export class Goals {
  private readonly api = inject(ApiService);

  protected readonly formatDateBr = formatDateBr;
  protected readonly goalStatusLabel = goalStatusLabel;

  protected readonly goals = signal<Goal[]>([]);
  protected readonly trainings = signal<Training[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly trainingCountByGoalId = computed(() => {
    const map = new Map<number, number>();
    for (const t of this.trainings()) {
      if (t.goalId != null) {
        map.set(t.goalId, (map.get(t.goalId) ?? 0) + 1);
      }
    }
    return map;
  });

  protected newTitle = '';
  protected newDescription = '';
  protected newTargetDate = '';
  protected newTargetMetric = '';

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      goals: this.api.listGoals(),
      trainings: this.api.listTrainings(),
    }).subscribe({
      next: ({ goals, trainings }) => {
        this.goals.set(goals);
        this.trainings.set(trainings);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Falha ao carregar metas: ' + (err?.message ?? err));
        this.loading.set(false);
      },
    });
  }

  protected createGoal(): void {
    const title = this.newTitle.trim();
    if (!title) {
      return;
    }
    const request: CreateGoalRequest = {
      title,
      description: this.newDescription.trim() || null,
      targetDate: this.newTargetDate || null,
      targetMetric: this.newTargetMetric.trim() || null,
    };
    this.api.createGoal(request).subscribe({
      next: () => {
        this.newTitle = '';
        this.newDescription = '';
        this.newTargetDate = '';
        this.newTargetMetric = '';
        this.reload();
      },
      error: (err) => {
        this.error.set('Falha ao criar meta: ' + (err?.message ?? err));
      },
    });
  }

  protected focusGoal(goal: Goal): void {
    this.api.focusGoal(goal.id).subscribe({
      next: () => this.reload(),
      error: (err) => {
        this.error.set('Falha ao marcar meta como foco: ' + (err?.message ?? err));
      },
    });
  }
}
