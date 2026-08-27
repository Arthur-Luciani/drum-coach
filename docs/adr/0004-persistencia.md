# ADR-0004: Persistência

## Status
Aceito

## Contexto
O app é local, single-user, e deve funcionar como um caderno duradouro (o usuário quer
poder olhar 6 meses para trás). Não há necessidade de um servidor de banco de dados
separado. O usuário é desenvolvedor e valoriza previsibilidade sobre "mágica" de ORM,
especialmente porque a auditoria de origem (ADR-0005) depende de controle preciso sobre
o que é escrito.

## Decisão
- SQLite como arquivo único de banco de dados, local à máquina do usuário.
- Acesso a dados via Spring Data JDBC (não JPA/Hibernate completo), na camada `infra`
  (ver [ADR-0006](0006-modulo-unico-em-camadas.md) — módulo único organizado por
  pacotes, não mais um módulo Maven `core` separado).
- Migrações de schema via Flyway.

## Consequências
- SQL mais explícito e previsível do que com Hibernate; menos automágica de cascata/estado
  gerenciado.
- Evita os atritos conhecidos entre Hibernate e o dialeto SQLite (sequences, `ALTER TABLE`
  limitado).
- Arquivo `.db` único é fácil de fazer backup e inspecionar com ferramentas genéricas de
  SQLite.
- Spring Data JDBC exige modelagem mais deliberada de agregados (menos lazy-loading
  automático), o que é aceitável dado o tamanho do domínio.
