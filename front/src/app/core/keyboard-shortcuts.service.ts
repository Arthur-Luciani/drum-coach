import { Injectable } from '@angular/core';

/**
 * Um escopo de atalhos de teclado. Cada tela/overlay que quer reagir a teclas registra
 * um scope via `KeyboardShortcutsService.register()` - nunca escuta `keydown` direto no
 * DOM (evitaria vazar handlers concorrentes entre telas, ex. `1` navegando E abrindo um
 * seletor numerado ao mesmo tempo).
 *
 * `handlers` usa a tecla normalizada (ver `normalizeKey`) como chave.
 *
 * `blockFallthrough` (default `true`) decide o que acontece quando a tecla apertada NAO
 * tem handler neste scope: por padrao a tecla e "engolida" aqui mesmo (comportamento de
 * modal/overlay - ex. o cheat-sheet aberto nao deixa `3` vazar pra navegacao por baixo
 * dele). So o scope global de navegacao (base da pilha) usa `blockFallthrough: false`,
 * deixando a busca continuar pros scopes abaixo dele (nesse caso, nenhum - ele e a base).
 */
export interface KeyScope {
  handlers: Record<string, (event: KeyboardEvent) => void>;
  blockFallthrough?: boolean;
}

/**
 * Escuta `keydown` no documento inteiro **uma unica vez** (singleton, `providedIn:
 * 'root'`) e distribui pra uma pilha de escopos registrados. O escopo do topo da pilha
 * tem prioridade; ver `KeyScope.blockFallthrough` pra regra de propagacao.
 *
 * Ignora por completo teclas digitadas em campos de formulario (input/textarea/select
 * ou qualquer elemento com `isContentEditable`) - nunca intercepta digitacao.
 *
 * Reusado por: navegacao global (1-5, `?`), overlay de cheat-sheet, e (fases futuras)
 * metronomo avulso (`M`), seletor de treino, Modo Sessao (Espaco/setas/Enter/Esc).
 */
@Injectable({ providedIn: 'root' })
export class KeyboardShortcutsService {
  private readonly scopes: KeyScope[] = [];
  private listening = false;

  /**
   * Empilha um scope de atalhos. Retorna uma funcao de "desregistrar" que remove esse
   * scope especifico da pilha (por identidade do objeto, nao por posicao - seguro mesmo
   * se scopes forem desregistrados fora de ordem).
   */
  register(scope: KeyScope): () => void {
    this.ensureListening();
    this.scopes.push(scope);
    return () => {
      const index = this.scopes.indexOf(scope);
      if (index !== -1) {
        this.scopes.splice(index, 1);
      }
    };
  }

  private ensureListening(): void {
    if (this.listening) {
      return;
    }
    this.listening = true;
    document.addEventListener('keydown', (event) => this.handleKeydown(event));
  }

  private handleKeydown(event: KeyboardEvent): void {
    if (this.isTypingTarget(event.target)) {
      return;
    }

    const key = normalizeKey(event.key);

    for (let i = this.scopes.length - 1; i >= 0; i--) {
      const scope = this.scopes[i];
      const handler = scope.handlers[key];
      if (handler) {
        event.preventDefault();
        handler(event);
        return;
      }
      if (scope.blockFallthrough !== false) {
        // Tecla sem handler neste scope, mas o scope bloqueia propagacao (comportamento
        // padrao de overlay/modal) - para a busca aqui, nao desce pros scopes abaixo.
        return;
      }
    }
  }

  private isTypingTarget(target: EventTarget | null): boolean {
    if (!(target instanceof HTMLElement)) {
      return false;
    }
    if (target.isContentEditable) {
      return true;
    }
    const tag = target.tagName.toLowerCase();
    return tag === 'input' || tag === 'textarea' || tag === 'select';
  }
}

/**
 * Normaliza `event.key` pra um formato consistente de chave de handler. Digitos ficam
 * como estao (`'1'`), simbolos como `'?'` ficam como estao, teclas nomeadas (`Escape`,
 * `Enter`, etc.) viram minusculo (`'escape'`, `'enter'`). Reusar sempre essa funcao ao
 * registrar handlers - nunca comparar `event.key` cru.
 */
export function normalizeKey(key: string): string {
  return key.length === 1 ? key : key.toLowerCase();
}
