/** Tipos espelhando exatamente os DTOs de `dev.drumcoach.presentation.web` (back/). */

export type Origin = 'USER' | 'CLAUDE';
export type ExerciseKind = 'TOCA_JUNTO' | 'TRANSCRICAO';
export type GoalStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'ACHIEVED' | 'ABANDONED';
export type RepertoireItemStatus = 'NOT_STARTED' | 'LEARNING' | 'MASTERED';
export type VideoSourceType = 'LINK' | 'FILE';

// ---------------------------------------------------------------------------
// Goal
// ---------------------------------------------------------------------------

export interface Goal {
  id: number;
  title: string;
  description: string | null;
  targetDate: string | null;
  status: GoalStatus;
  targetMetric: string | null;
  inFocus: boolean;
  createdBy: Origin;
  lastModifiedBy: Origin;
  createdAt: string;
  updatedAt: string;
}

export interface CreateGoalRequest {
  title: string;
  description?: string | null;
  targetDate?: string | null;
  targetMetric?: string | null;
}

export interface UpdateGoalRequest {
  status?: GoalStatus | null;
  description?: string | null;
  inFocus?: boolean | null;
}

export interface TrainingProgress {
  trainingId: number;
  name: string;
  targetRepetitions: number | null;
  completedCount: number;
}

export interface GoalDetail {
  goal: Goal;
  trainings: TrainingProgress[];
}

// ---------------------------------------------------------------------------
// Training - pertence direto a Meta (goalId nullable = treino avulso, ver ADR-0009)
// ---------------------------------------------------------------------------

export interface Training {
  id: number;
  goalId: number | null;
  name: string;
  description: string | null;
  targetDurationMinutes: number;
  targetRepetitions: number | null;
  orderIndex: number;
  createdBy: Origin;
  lastModifiedBy: Origin;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTrainingRequest {
  goalId?: number | null;
  name: string;
  description?: string | null;
  targetDurationMinutes: number;
  targetRepetitions?: number | null;
  orderIndex: number;
}

/** `PATCH /api/trainings/{id}` - edicao parcial; todo campo `null`/omitido mantem o atual
 * (inclusive `goalId`: `null` nao desvincula o treino da meta). */
export interface UpdateTrainingRequest {
  name?: string | null;
  description?: string | null;
  targetDurationMinutes?: number | null;
  targetRepetitions?: number | null;
  goalId?: number | null;
  orderIndex?: number | null;
}

// ---------------------------------------------------------------------------
// Exercise
// ---------------------------------------------------------------------------

export interface Exercise {
  id: number;
  trainingId: number;
  name: string;
  /** Eixo novo (ADR-0011): decide COMO o exercicio e executado/renderizado. Imutavel. */
  kind: ExerciseKind;
  exerciseType: string | null;
  howToExecute: string | null;
  /** Documento tocavel - preenchido so em `TOCA_JUNTO` (ver `DrumPattern` abaixo). */
  pattern: DrumPattern | null;
  /** Trechos marcados - so relevante em `TRANSCRICAO`, acumulados ao longo da pratica. */
  passages: MarkedPassage[];
  targetBpm: number | null;
  targetDurationSeconds: number | null;
  videoSourceType: VideoSourceType | null;
  videoUrl: string | null;
  videoFilePath: string | null;
  orderIndex: number;
  createdBy: Origin;
  lastModifiedBy: Origin;
  createdAt: string;
  updatedAt: string;
}

export interface CreateExerciseRequest {
  name: string;
  kind: ExerciseKind;
  exerciseType?: string | null;
  howToExecute?: string | null;
  pattern?: DrumPattern | null;
  targetBpm?: number | null;
  targetDurationSeconds?: number | null;
  videoSourceType?: VideoSourceType | null;
  videoUrl?: string | null;
  videoFilePath?: string | null;
  orderIndex: number;
}

/** `PATCH /api/exercises/{id}` - edicao parcial de qualquer campo editavel; todo campo
 * `null`/omitido mantem o atual. `pattern` substitui o documento inteiro (nao e um diff).
 * `kind` NAO e editavel (ver ADR-0011). */
export interface UpdateExerciseRequest {
  name?: string | null;
  exerciseType?: string | null;
  howToExecute?: string | null;
  pattern?: DrumPattern | null;
  targetBpm?: number | null;
  targetDurationSeconds?: number | null;
  videoSourceType?: VideoSourceType | null;
  videoUrl?: string | null;
  videoFilePath?: string | null;
  orderIndex?: number | null;
}

/** Trecho marcado de uma transcricao (tabela filha `exercise_passage`, ver ADR-0011). */
export interface MarkedPassage {
  id: number;
  fromSeconds: number;
  toSeconds: number | null;
  label: string | null;
  createdAt: string;
}

export interface MarkedPassageRequest {
  fromSeconds: number;
  toSeconds?: number | null;
  label?: string | null;
}

// ---------------------------------------------------------------------------
// DrumPattern - documento tocavel de um exercicio `TOCA_JUNTO` (ver ADR-0011).
// Espelha o schema validado pelo back (`DrumPattern.java`): so estrutura, sem nada
// derivado. Valor ritmico, ligadura (beam), pausa e posicao na pauta sao calculados
// em runtime pelo `PatternEngraver` (front/src/app/shared/pattern-engraver.ts) e
// nunca persistidos.
// ---------------------------------------------------------------------------

/** Vocabulario fixo de vozes de um padrao. Ordem irrelevante aqui - a ordem de
 * empilhamento na pauta e no dot-grid e decidida pelo engraver. */
export type DrumVoice =
  | 'crash'
  | 'ride'
  | 'hihat'
  | 'hiTom'
  | 'midTom'
  | 'floorTom'
  | 'snare'
  | 'kick';

export interface DrumPattern {
  /** Versao do documento - permite evoluir o schema sem quebrar padroes salvos. */
  version: 1;
  /** [numerador, denominador], ex: [4, 4]. */
  timeSignature: [number, number];
  /** Quantos steps (subdivisoes) cabem em um tempo (beat). */
  stepsPerBeat: number;
  /** `true` = a subdivisao e uma tercina/quialtera (sem beam de semicolcheia). */
  tuplet: boolean;
  /** Numero de compassos do loop. */
  bars: number;
  /** Vozes presentes no padrao (subconjunto do vocabulario). */
  voices: DrumVoice[];
  /** Por voz: indices de step 0-based sobre o loop inteiro
   * (`total = bars * timeSignature[0] * stepsPerBeat`). */
  hits: Partial<Record<DrumVoice, number[]>>;
  /** Opcional. Por voz: subconjunto dos steps de `hits` que sao acentuados. */
  accents?: Partial<Record<DrumVoice, number[]>>;
  /** Opcional. So faz sentido em padrao de voz unica (rudimento): a sequencia de
   * maos, indexada pela ordem dos hits da voz (ciclica). */
  sticking?: ('R' | 'L')[];
}

// ---------------------------------------------------------------------------
// Execution
// ---------------------------------------------------------------------------

export interface ExecutionExerciseLog {
  id: number;
  exerciseId: number;
  achievedBpm: number | null;
  actualDurationSeconds: number | null;
  notes: string | null;
  createdAt: string;
}

export interface ExecutionExerciseLogRequest {
  exerciseId: number;
  achievedBpm?: number | null;
  actualDurationSeconds?: number | null;
  notes?: string | null;
}

export interface Execution {
  id: number;
  /** `null` = sessão livre (Modo Sessão sem treino escolhido, ver ADR-0009 seguinte). */
  trainingId: number | null;
  executionDate: string;
  actualDurationMinutes: number | null;
  feeling: string | null;
  generalNotes: string | null;
  goalId: number | null;
  logs: ExecutionExerciseLog[];
  createdBy: Origin;
  lastModifiedBy: Origin;
  createdAt: string;
  updatedAt: string;
}

export interface CreateExecutionRequest {
  /** `null` = sessão livre (sem treino vinculado - só cronômetro/metrônomo). */
  trainingId: number | null;
  executionDate: string;
  actualDurationMinutes?: number | null;
  feeling?: string | null;
  generalNotes?: string | null;
  goalId?: number | null;
  logs?: ExecutionExerciseLogRequest[];
}

// ---------------------------------------------------------------------------
// Lesson
// ---------------------------------------------------------------------------

export interface Lesson {
  id: number;
  lessonDate: string;
  teacherNotes: string | null;
  feedback: string | null;
  focusUntilNext: string | null;
  suggestedMaterial: string | null;
  generatedTrainingId: number | null;
  createdBy: Origin;
  lastModifiedBy: Origin;
  createdAt: string;
  updatedAt: string;
}

export interface CreateLessonRequest {
  lessonDate: string;
  teacherNotes?: string | null;
  feedback?: string | null;
  focusUntilNext?: string | null;
  suggestedMaterial?: string | null;
  generatedTrainingId?: number | null;
}

// ---------------------------------------------------------------------------
// RepertoireItem
// ---------------------------------------------------------------------------

export interface RepertoireLink {
  id: number;
  url: string;
  label: string | null;
  createdAt: string;
}

export interface RepertoireLinkRequest {
  url: string;
  label?: string | null;
}

export interface RepertoireItem {
  id: number;
  songTitle: string;
  artist: string | null;
  status: RepertoireItemStatus;
  targetBpm: number | null;
  currentBpm: number | null;
  notes: string | null;
  links: RepertoireLink[];
  createdBy: Origin;
  lastModifiedBy: Origin;
  createdAt: string;
  updatedAt: string;
}

export interface CreateRepertoireItemRequest {
  songTitle: string;
  artist?: string | null;
  /** Status inicial - `null`/omitido assume `NOT_STARTED`. */
  status?: RepertoireItemStatus | null;
  targetBpm?: number | null;
  currentBpm?: number | null;
  notes?: string | null;
  links?: RepertoireLinkRequest[];
}

export interface UpdateRepertoireItemRequest {
  status?: RepertoireItemStatus | null;
  currentBpm?: number | null;
  notes?: string | null;
  newLinks?: RepertoireLinkRequest[];
}
