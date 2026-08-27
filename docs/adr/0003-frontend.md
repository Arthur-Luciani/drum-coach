# ADR-0003: Frontend

## Status
Aceito

## Contexto
O usuário quer acessar o caderno pelo navegador, localmente, e prefere não depender de
renderização server-side (receio de o Thymeleaf "travar" a evolução da interface). Ele não
pretende se aprofundar no código do frontend.

## Decisão
SPA em Angular (TypeScript), consumindo a API REST exposta pelo adapter
`presentation.web` (ver [ADR-0006](0006-modulo-unico-em-camadas.md)). O build de
produção do Angular é empacotado dentro do único jar do projeto, integrado ao Maven via
plugin de frontend.

## Consequências
- Dois ecossistemas de build (Maven + npm/Angular CLI) integrados num único pipeline —
  mais setup inicial do que uma solução server-rendered.
- Frontend fica desacoplado do backend por contrato HTTP, facilitando evolução visual
  sem tocar no `core`/`mcp-server`.
- Em desenvolvimento, o Angular roda com seu próprio dev server (proxy para a API);
  em produção, um único processo (`web`) serve tudo.
