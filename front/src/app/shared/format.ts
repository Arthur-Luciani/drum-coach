/**
 * Funcoes puras de formatacao de exibicao (datas/duracoes), compartilhadas entre as
 * telas administrativas pra bater com o estilo do mockup Woodshed. Sem estado, sem
 * dependencia de Angular - so texto derivado dos dados que ja existem.
 */

const MONTHS_PT = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** '2026-08-26' -> '26/08/2026'. Datas ISO (yyyy-MM-dd) vindas da API/formularios. */
export function formatDateBr(iso: string | null | undefined): string | null {
  if (!iso) {
    return null;
  }
  const [y, m, d] = iso.split('-');
  if (!y || !m || !d) {
    return iso;
  }
  return `${d}/${m}/${y}`;
}

/** '2026-08-26' -> '26 ago' (sem ano, mes abreviado em portugues - ver ExecutionsHistory/Lessons no mockup). */
export function formatDateShort(iso: string | null | undefined): string | null {
  if (!iso) {
    return null;
  }
  const [, m, d] = iso.split('-');
  const monthIndex = Number(m) - 1;
  if (!d || monthIndex < 0 || monthIndex > 11 || Number.isNaN(monthIndex)) {
    return iso;
  }
  return `${Number(d)} ${MONTHS_PT[monthIndex]}`;
}

/** Segundos -> texto curto tipo '4 min' (ou '45s' se menor que um minuto). */
export function formatDurationLabel(totalSeconds: number | null | undefined): string | null {
  if (totalSeconds == null) {
    return null;
  }
  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }
  return `${Math.round(totalSeconds / 60)} min`;
}

/** Segundos -> 'M:SS' (minuto sem zero a esquerda, segundos sempre com 2 digitos). Usado
 * pelo timer do exercicio no Modo Sessao (tempo decorrido e tempo previsto). */
export function formatClock(totalSeconds: number): string {
  const clamped = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(clamped / 60);
  const seconds = clamped % 60;
  return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

/** Minutos -> texto curto tipo '1h30' (ou '45 min' se menor que 1h, '2h' se exato). Usado
 * pra duracao alvo de treinos (`Training.targetDurationMinutes`), ex. no `TrainingPicker`. */
export function formatDurationMinutesLabel(totalMinutes: number | null | undefined): string | null {
  if (totalMinutes == null) {
    return null;
  }
  if (totalMinutes < 60) {
    return `${totalMinutes} min`;
  }
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return minutes > 0 ? `${hours}h${String(minutes).padStart(2, '0')}` : `${hours}h`;
}
