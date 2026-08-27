# ADR-0006: Módulo único Maven, organizado em camadas DDD/Clean Architecture

## Status
Aceito. Substitui [ADR-0002](0002-arquitetura-de-modulos-e-processos.md). A parte deste
ADR relativa a `presentation.mcp` como entry point Spring Boot no mesmo jar foi ajustada
por [ADR-0007](0007-mcp-como-proxy-http.md) — o backend continua como descrito aqui
(camadas domain/application/infra/presentation.web), mas o MCP virou um projeto/processo
separado no monorepo, não mais um segundo entry point deste módulo.

## Contexto
O ADR-0002 propunha três módulos Maven (`core`, `web`, `mcp-server`) separados por
fronteira de deploy. Em revisão com o usuário (desenvolvedor do projeto, que quer manter
o código a longo prazo e pediu controle explícito sobre a arquitetura), duas coisas
mudaram:

1. Ele quer camadas explícitas ao estilo DDD/Clean Architecture — `domain` (regras de
   negócio puras), `application` (casos de uso/orquestração), `infra` (persistência,
   configuração, mapeamento), `presentation` (adapters de entrada) — em vez de módulos
   organizados só por unidade de deploy.
2. Ele questionou a necessidade de múltiplos módulos Maven: para ele, `web` e `mcp` são
   "portas diferentes da mesma aplicação", e um único `pom.xml` já é suficiente já que o
   projeto é pequeno o bastante para não precisar de fronteiras físicas de build entre
   camadas — package-level já basta.

Continua existindo, porém, uma restrição técnica real: o transporte stdio do MCP exige
que o client (Claude Desktop) seja quem inicia o processo do servidor MCP — não é
possível conectar um client MCP-stdio a um processo já em execução (ex: o app web que o
usuário abriu manualmente). Por isso, mesmo em módulo único, o app precisa de dois
*entry points* (duas classes `main`) que rodam como dois processos separados em runtime.

## Decisão
Um único módulo Maven (`pom.xml` na raiz), com o código organizado em pacotes por
camada:

- `dev.drumcoach.domain`: entidades/agregados e regras de negócio, sem dependência de
  Spring, JDBC ou qualquer framework.
- `dev.drumcoach.application`: casos de uso (orquestração), define os *ports* que a
  camada de infraestrutura implementa (ex.: `OriginProvider`, interfaces de repositório).
- `dev.drumcoach.infra`: implementações dos ports — repositórios Spring Data JDBC,
  mapeamento domínio ↔ registros JDBC, configuração (DataSource, Flyway,
  `JdbcCustomConversions`).
- `dev.drumcoach.presentation.web`: adapter HTTP — `WebApplication` (entry point),
  controllers REST, DTOs, e a implementação de `OriginProvider` fixa em `USER`.
- `dev.drumcoach.presentation.mcp`: adapter MCP — `McpApplication` (entry point),
  ferramentas de leitura/escrita, e a implementação de `OriginProvider` fixa em `CLAUDE`.

Os dois entry points (`WebApplication` e `McpApplication`) coexistem no mesmo jar, mas
são executados como processos separados (`java -cp app.jar <FQCN>`), cada um com
`@SpringBootApplication(scanBasePackages = ...)` restrito ao seu próprio pacote de
presentation mais `domain`/`application`/`infra` — nunca escaneando o pacote de
presentation do outro, para não colidir em beans (ex.: dois `OriginProvider`
concorrentes).

## Consequências
- Um único `pom.xml`, sem overhead de projeto multi-módulo Maven.
- As fronteiras entre camadas são por convenção de pacote, não impostas fisicamente pelo
  build — checagem de disciplina fica a cargo de revisão de código (ou, futuramente, uma
  ferramenta como ArchUnit, não incluída no MVP).
- `web` e `mcp` continuam sendo dois processos em runtime, mas isso é justificado
  exclusivamente pela restrição de transporte do MCP, não por uma escolha de
  "microsserviços" — se o Claude Desktop passar a suportar bem MCP via HTTP/SSE local no
  futuro, os dois entry points podem ser unificados em um só sem reestruturar módulos
  (já é tudo o mesmo módulo).
- Cuidado operacional necessário na Fase 0: garantir que o processo `McpApplication` não
  escreva nada em stdout além do protocolo MCP (logs devem ir para stderr/arquivo), já
  que o framing JSON-RPC do stdio quebra com qualquer saída extra.
