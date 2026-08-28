import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  CreateExecutionRequest,
  CreateExerciseRequest,
  CreateGoalRequest,
  CreateLessonRequest,
  CreateRepertoireItemRequest,
  CreateTrainingRequest,
  Exercise,
  Execution,
  Goal,
  GoalDetail,
  Lesson,
  MarkedPassage,
  MarkedPassageRequest,
  RepertoireItem,
  Training,
  UpdateExerciseRequest,
  UpdateGoalRequest,
  UpdateRepertoireItemRequest,
  UpdateTrainingRequest,
} from './models';

/**
 * Cliente HTTP unico para a API REST do back (`presentation.web`). Mantem todas as
 * chamadas num so lugar para nao espalhar strings de URL pelos componentes.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // Goals ---------------------------------------------------------------
  listGoals(): Observable<Goal[]> {
    return this.http.get<Goal[]>('/api/goals');
  }

  getGoal(id: number): Observable<GoalDetail> {
    return this.http.get<GoalDetail>(`/api/goals/${id}`);
  }

  createGoal(request: CreateGoalRequest): Observable<Goal> {
    return this.http.post<Goal>('/api/goals', request);
  }

  updateGoal(id: number, request: UpdateGoalRequest): Observable<Goal> {
    return this.http.patch<Goal>(`/api/goals/${id}`, request);
  }

  focusGoal(id: number): Observable<Goal> {
    return this.updateGoal(id, { inFocus: true });
  }

  // Trainings - pertencem direto a Meta (goalId nulo = treino avulso, ver ADR-0009) ----
  listTrainings(goalId?: number): Observable<Training[]> {
    const params = goalId != null ? new HttpParams().set('goalId', goalId) : undefined;
    return this.http.get<Training[]>('/api/trainings', { params });
  }

  createTraining(request: CreateTrainingRequest): Observable<Training> {
    return this.http.post<Training>('/api/trainings', request);
  }

  /** Edicao parcial de um treino (campos omitidos ficam inalterados). */
  updateTraining(id: number, request: UpdateTrainingRequest): Observable<Training> {
    return this.http.patch<Training>(`/api/trainings/${id}`, request);
  }

  /** Apaga o treino e, em cascata, seus exercicios/trechos. Responde 409 se houver
   * execucoes registradas neste treino. */
  deleteTraining(id: number): Observable<void> {
    return this.http.delete<void>(`/api/trainings/${id}`);
  }

  // Exercises ---------------------------------------------------------------
  listExercises(trainingId: number): Observable<Exercise[]> {
    return this.http.get<Exercise[]>(`/api/trainings/${trainingId}/exercises`);
  }

  createExercise(trainingId: number, request: CreateExerciseRequest): Observable<Exercise> {
    return this.http.post<Exercise>(`/api/trainings/${trainingId}/exercises`, request);
  }

  /** Um exercicio com `kind`/`pattern`/`passages` - usado pelo editor de exercicio
   * (ADR-0011). A lista de `listExercises` ja traz os mesmos campos; este e o fetch
   * pontual de um so. */
  getExercise(id: number): Observable<Exercise> {
    return this.http.get<Exercise>(`/api/exercises/${id}`);
  }

  /** Edita `pattern` (TOCA_JUNTO) e/ou `howToExecute` (nota livre / notas de
   * transcricao). `kind` nao e editavel. */
  updateExercise(id: number, request: UpdateExerciseRequest): Observable<Exercise> {
    return this.http.patch<Exercise>(`/api/exercises/${id}`, request);
  }

  /** Apaga o exercicio e seus trechos marcados em cascata. Responde 409 se houver
   * execucao com log deste exercicio. */
  deleteExercise(id: number): Observable<void> {
    return this.http.delete<void>(`/api/exercises/${id}`);
  }

  addPassage(exerciseId: number, request: MarkedPassageRequest): Observable<MarkedPassage> {
    return this.http.post<MarkedPassage>(`/api/exercises/${exerciseId}/passages`, request);
  }

  deletePassage(exerciseId: number, passageId: number): Observable<void> {
    return this.http.delete<void>(`/api/exercises/${exerciseId}/passages/${passageId}`);
  }

  // Executions ---------------------------------------------------------------
  listExecutions(filter?: { trainingId?: number; goalId?: number }): Observable<Execution[]> {
    let params = new HttpParams();
    if (filter?.trainingId != null) {
      params = params.set('trainingId', filter.trainingId);
    } else if (filter?.goalId != null) {
      params = params.set('goalId', filter.goalId);
    }
    return this.http.get<Execution[]>('/api/executions', { params });
  }

  createExecution(request: CreateExecutionRequest): Observable<Execution> {
    return this.http.post<Execution>('/api/executions', request);
  }

  // Lessons ---------------------------------------------------------------
  listLessons(from?: string, to?: string): Observable<Lesson[]> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<Lesson[]>('/api/lessons', { params });
  }

  createLesson(request: CreateLessonRequest): Observable<Lesson> {
    return this.http.post<Lesson>('/api/lessons', request);
  }

  // Repertoire ---------------------------------------------------------------
  listRepertoireItems(): Observable<RepertoireItem[]> {
    return this.http.get<RepertoireItem[]>('/api/repertoire-items');
  }

  createRepertoireItem(request: CreateRepertoireItemRequest): Observable<RepertoireItem> {
    return this.http.post<RepertoireItem>('/api/repertoire-items', request);
  }

  updateRepertoireItem(id: number, request: UpdateRepertoireItemRequest): Observable<RepertoireItem> {
    return this.http.patch<RepertoireItem>(`/api/repertoire-items/${id}`, request);
  }
}
