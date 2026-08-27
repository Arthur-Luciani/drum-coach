# ADR-0005: Auditoria de origem das escritas

## Status
Proposto (escopo do MVP sujeito a confirmação — ver "Consequências")

## Contexto
Requisito explícito do usuário: toda escrita feita através do MCP (ou seja, pelo Claude)
precisa ser identificável como tal, para diferenciar do que foi escrito manualmente pelo
próprio usuário na interface web.

## Decisão
Cada tabela de domínio (planos, treinos, exercícios, execuções, metas, aulas, itens de
repertório) inclui:
- `created_by` e `last_modified_by`: enum `USER` | `CLAUDE`.
- `created_at` e `updated_at`: timestamp.

Esses campos são preenchidos exclusivamente pelos casos de uso da camada `application`
(ver [ADR-0006](0006-modulo-unico-em-camadas.md)) — nem os controllers REST
(`presentation.web`) nem as ferramentas MCP (`presentation.mcp`) escrevem diretamente no
repositório de dados; ambos passam pelos mesmos casos de uso, que identificam o autor via
o port `OriginProvider`, implementado de forma fixa por cada entry point.

## Consequências
- Não há como uma escrita "esquecer" de marcar sua origem, pois a responsabilidade não é
  de cada endpoint/tool individualmente.
- **Fora do escopo do MVP:** um log de auditoria completo (histórico campo-a-campo de cada
  alteração, tipo event sourcing). A decisão atual só marca *quem escreveu por último* e
  *quando criou*, não o histórico de todas as alterações anteriores. Se o usuário quiser
  rastrear "o Claude mudou o BPM alvo de X para Y no dia tal", isso exigiria uma tabela de
  auditoria append-only separada — a ser avaliada como evolução futura, não MVP.
