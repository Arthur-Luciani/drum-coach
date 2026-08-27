# ADR-0001: Linguagem e runtime do backend

## Status
Aceito

## Contexto
O aplicativo é de uso pessoal, local, single-user, e será mantido pelo próprio usuário
(desenvolvedor Java). Precisa integrar com o Claude via MCP (Model Context Protocol) e
modelar um domínio relacional razoavelmente estruturado (planos de treino, treinos,
exercícios, execuções, metas, aulas, repertório). Existe também a possibilidade futura de
captura de eventos MIDI de uma bateria eletrônica (Roland TD-07) para análise de
timing/andamento.

Alternativas consideradas: TypeScript/Node (SDK MCP com ótimo suporte, mas o usuário não
manteria com a mesma confiança) e Python (SDK MCP de referência, boas libs de MIDI/áudio,
mas conhecimento do usuário é limitado).

## Decisão
Java (JDK LTS mais recente disponível) com Spring Boot como base do backend, usando o SDK
oficial de MCP para Java (`modelcontextprotocol/java-sdk`).

## Consequências
- Tipagem forte e ecossistema maduro para um domínio que vai crescer em regras de negócio
  (progressão de planos, contagem de execuções, auditoria).
- `javax.sound.midi` é nativo no JDK, o que mantém aberta a porta para integração futura
  com a TD-07 sem precisar trocar de stack.
- Build/startup mais pesados que Node/Python, aceitável para um app local de uso pessoal.
- Familiaridade do usuário com Java favorece manutenção de longo prazo, que é um requisito
  explícito.
