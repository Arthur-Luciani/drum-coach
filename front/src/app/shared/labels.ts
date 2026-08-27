import { GoalStatus, RepertoireItemStatus } from '../models';

/**
 * Traducoes de enums pra portugues, so pra exibicao (nenhuma logica depende delas -
 * o valor bruto do enum continua sendo o que vai pra API/CSS). Compartilhado entre
 * Goals/GoalDetail (GoalStatus) e Repertoire (RepertoireItemStatus) pra nao repetir o
 * mapa em cada tela.
 */
const GOAL_STATUS_LABELS: Record<GoalStatus, string> = {
  NOT_STARTED: 'não iniciada',
  IN_PROGRESS: 'em andamento',
  ACHIEVED: 'alcançada',
  ABANDONED: 'abandonada',
};

export function goalStatusLabel(status: GoalStatus): string {
  return GOAL_STATUS_LABELS[status] ?? status;
}

const REPERTOIRE_STATUS_LABELS: Record<RepertoireItemStatus, string> = {
  NOT_STARTED: 'não iniciada',
  LEARNING: 'aprendendo',
  MASTERED: 'dominada',
};

export function repertoireStatusLabel(status: RepertoireItemStatus): string {
  return REPERTOIRE_STATUS_LABELS[status] ?? status;
}
