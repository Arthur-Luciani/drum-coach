# ADR-0002: Arquitetura de módulos e processos (web + MCP)

## Status
Substituído por [ADR-0006](0006-modulo-unico-em-camadas.md). A divisão em três módulos
Maven (`core`/`web`/`mcp-server`) foi revista a pedido do usuário em favor de um único
módulo organizado por camadas (domain/application/infra/presentation). O raciocínio
sobre por que `web` e `mcp-server` continuam sendo dois *processos* em runtime (restrição
do transporte stdio do MCP) permanece válido e foi carregado para o ADR-0006 — o que
mudou foi a granularidade do *build* (módulos Maven), não essa parte.

## Contexto
O app web local (o "caderno") e o servidor MCP (usado pelo Claude Desktop) precisam ler e
escrever os mesmos dados. O transporte HTTP/SSE para servidores MCP locais tem suporte
inconsistente no Claude Desktop no momento desta decisão; o transporte stdio (o cliente
inicia o processo do servidor MCP diretamente) é o caminho mais compatível e testado.
Além disso, é um requisito que o app web funcione sem o Claude aberto, e que o Claude
consiga escrever no caderno mesmo com o app web fechado.

## Decisão
Projeto multi-módulo Maven com três módulos:

- `core`: modelo de domínio, acesso a dados (via módulo de persistência), e a lógica de
  auditoria de origem das escritas (ver ADR-0005). Nenhum módulo escreve no banco sem
  passar por aqui.
- `web`: aplicação Spring Boot que expõe a API REST consumida pela SPA Angular (ver
  ADR-0003) e serve o build da SPA.
- `mcp-server`: processo Java separado, com transporte stdio, que expõe as ferramentas MCP
  (consulta e escrita) usando o `core` diretamente.

Os dois processos (`web` e `mcp-server`) apontam para o mesmo arquivo SQLite local — não
há comunicação de rede entre eles.

## Consequências
- `web` e `mcp-server` rodam de forma independente; cada um funciona sem o outro estar
  ativo.
- Nenhuma dependência do suporte a HTTP/SSE do Claude Desktop para MCP local.
- Auditoria de origem fica centralizada no `core`, então tanto a API REST quanto as
  ferramentas MCP passam pelo mesmo caminho de escrita.
- Se no futuro fizer sentido migrar o `mcp-server` para HTTP/SSE (ex: uso remoto), a
  mudança fica isolada a esse módulo.
