package dev.drumcoach.domain;

/**
 * Discriminador de {@link Exercise} (ver ADR-0011): decide como o exercicio e
 * executado/renderizado. Eixo separado de {@code exerciseType} (texto livre que descreve
 * <em>o que</em> o exercicio treina).
 *
 * <ul>
 * <li>{@link #TOCA_JUNTO} - tem um padrao tocavel ({@code pattern}, JSON) que roda em loop
 * com metronomo e count-in.</li>
 * <li>{@link #TRANSCRICAO} - trabalho de ouvido: notas livres ({@code howToExecute}) +
 * trechos marcados ({@link ExercisePassage}).</li>
 * </ul>
 *
 * Imutavel apos a criacao do exercicio - trocar o tipo troca o que o registro significa
 * (ver {@code UpdateExerciseUseCase}).
 */
public enum ExerciseKind {
	TOCA_JUNTO,
	TRANSCRICAO
}
