import { Component, computed, input } from '@angular/core';

/**
 * Barra de progresso compartilhada. Recebe um valor e uma meta (nullable - nem todo
 * treino/meta tem uma meta de repeticoes definida) e decide sozinha a porcentagem
 * (capada em 100%) e se usa a cor de acento normal ou a cor "completo" (mesma logica que
 * `.progress-bar.complete` ja tinha, so que centralizada aqui em vez de calculada em cada
 * pagina).
 *
 * Sem `max` (null/undefined) nao ha meta definida: nao faz sentido desenhar uma barra
 * preenchida arbitrariamente, entao o componente nao renderiza nada nesse caso - a tela
 * que o usa mostra a contagem bruta como texto (ex: "3 execucoes (sem meta definida)").
 */
@Component({
  selector: 'app-progress-bar',
  template: `
    @if (max(); as m) {
      <div class="progress-bar" [class.complete]="complete()">
        <span [style.width.%]="percent()"></span>
      </div>
    }
  `,
})
export class ProgressBar {
  readonly value = input.required<number>();
  readonly max = input<number | null | undefined>(null);

  protected readonly percent = computed(() => {
    const max = this.max();
    if (!max || max <= 0) {
      return 0;
    }
    return Math.min(100, Math.round((this.value() / max) * 100));
  });

  protected readonly complete = computed(() => {
    const max = this.max();
    return max != null && max > 0 && this.value() >= max;
  });
}
