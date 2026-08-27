import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../api';
import { CreateLessonRequest, Goal, Lesson, Training } from '../../models';
import { formatDateShort } from '../../shared/format';

@Component({
  selector: 'app-lessons',
  imports: [FormsModule],
  templateUrl: './lessons.html',
})
export class Lessons {
  private readonly api = inject(ApiService);

  protected readonly formatDateShort = formatDateShort;

  protected readonly lessons = signal<Lesson[]>([]);
  protected readonly trainings = signal<Training[]>([]);
  protected readonly goals = signal<Goal[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly trainingById = computed(() => {
    const map = new Map<number, Training>();
    for (const t of this.trainings()) {
      map.set(t.id, t);
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

  /** Texto "gerou o treino X na meta Y" (ou so "X" se o treino for avulso). */
  protected generatedTrainingLabel(trainingId: number): string {
    const training = this.trainingById().get(trainingId);
    if (!training) {
      return `gerou o treino #${trainingId}`;
    }
    const goalTitle = training.goalId != null ? this.goalTitleById().get(training.goalId) : null;
    return goalTitle
      ? `gerou o treino "${training.name}" na meta "${goalTitle}"`
      : `gerou o treino "${training.name}"`;
  }

  protected lessonDate = new Date().toISOString().slice(0, 10);
  protected teacherNotes = '';
  protected feedback = '';
  protected focusUntilNext = '';
  protected suggestedMaterial = '';

  constructor() {
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      lessons: this.api.listLessons(),
      trainings: this.api.listTrainings(),
      goals: this.api.listGoals(),
    }).subscribe({
      next: ({ lessons, trainings, goals }) => {
        const sorted = [...lessons].sort((a, b) => (a.lessonDate < b.lessonDate ? 1 : -1));
        this.lessons.set(sorted);
        this.trainings.set(trainings);
        this.goals.set(goals);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Falha ao carregar aulas: ' + (err?.message ?? err));
        this.loading.set(false);
      },
    });
  }

  protected createLesson(): void {
    if (!this.lessonDate) {
      return;
    }
    const request: CreateLessonRequest = {
      lessonDate: this.lessonDate,
      teacherNotes: this.teacherNotes.trim() || null,
      feedback: this.feedback.trim() || null,
      focusUntilNext: this.focusUntilNext.trim() || null,
      suggestedMaterial: this.suggestedMaterial.trim() || null,
      generatedTrainingId: null,
    };
    this.api.createLesson(request).subscribe({
      next: () => {
        this.teacherNotes = '';
        this.feedback = '';
        this.focusUntilNext = '';
        this.suggestedMaterial = '';
        this.reload();
      },
      error: (err) => {
        this.error.set('Falha ao criar aula: ' + (err?.message ?? err));
      },
    });
  }
}
