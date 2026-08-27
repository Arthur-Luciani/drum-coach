# ADR-0007: MCP como proxy HTTP fino sobre a API REST, monorepo back/front/mcp

## Status
Aceito. Ajusta [ADR-0006](0006-modulo-unico-em-camadas.md): a parte que descrevia
`presentation.mcp` como um segundo entry point Spring Boot rodando no mesmo jar do
backend, compartilhando `application`/`infra` em processo, **deixa de valer**. O restante
do ADR-0006 (camadas `domain`/`application`/`infra`/`presentation.web` dentro do backend)
continua válido.

## Contexto
O desenho original (ADR-0006) resolvia a exigência de dois processos (web e MCP, por
causa do transporte stdio) fazendo os dois compartilharem o mesmo jar/módulo, com
`scanBasePackages` restrito por entry point para não colidir em beans. Na prática, isso
exigia bastante cuidado: dois `@SpringBootApplication` no mesmo classpath, risco real de
colisão de beans (ex.: dois `OriginProvider`), e atenção a não poluir o stdout do
processo MCP com logging do Spring Boot.

Em revisão com o usuário, ficou claro que o MCP não precisa conhecer domínio nem
persistência — ele só precisa traduzir chamadas de ferramentas MCP em chamadas para a API
REST que o backend já expõe para o front. Ou seja, o MCP pode ser um **cliente HTTP fino**
da mesma API, não mais um adapter que compartilha código de aplicação/infra em processo.

## Decisão

**Monorepo com três pastas de topo:**
```
drum-coach/
├── back/    # Spring Boot: domain/application/infra/presentation.web (ver ADR-0006)
├── front/   # Angular SPA
└── mcp/     # proxy MCP — projeto Java independente, SEM Spring Boot
```

- `mcp/` é um módulo Maven próprio, com seu próprio `pom.xml`, dependendo só do SDK MCP
  Java (`io.modelcontextprotocol.sdk`) e de um cliente HTTP (o `java.net.http.HttpClient`
  nativo do JDK é suficiente — sem necessidade de puxar Spring Boot para um processo que
  só faz chamadas HTTP simples e fala stdio).
- Cada ferramenta MCP (`list_active_plans`, `record_execution`, etc. — ver plano de
  implementação) só monta a requisição HTTP correspondente para `back` e traduz a
  resposta para o formato de retorno da tool. Nenhuma lógica de negócio, nenhuma
  dependência de `infra`/`application`.
- **Identificação de origem (`created_by`/`last_modified_by`, ADR-0005)**: `mcp/` envia o
  header `X-Drum-Coach-Actor: CLAUDE` em toda chamada HTTP para `back`. Um interceptor no
  `presentation.web` do backend lê esse header e resolve o `Origin` da requisição — ausente
  o header, o `Origin` é `USER` (chamadas do `front` continuam sem precisar desse header).
  Isso substitui o mecanismo antigo de `OriginProvider` como bean fixo por processo: agora
  `OriginProvider` (o port em `application`) é resolvido por requisição, mas continua
  sendo o único ponto que os casos de uso consultam — a regra de auditoria em si não muda,
  só a forma como a origem chega até ela.
- `back` precisa estar em execução para o MCP funcionar (diferente do desenho anterior,
  em que os dois processos eram totalmente independentes). Isso é uma troca aceita: o app
  é de uso pessoal e local — rodar `back` continuamente (ex.: iniciar uma vez e deixar
  rodando) é uma expectativa razoável, e simplifica bastante o processo MCP.

## Consequências
- Elimina o risco técnico mais complicado do ADR-0006 (dois entry points Spring Boot
  dividindo classpath/jar). O processo MCP agora é deliberadamente simples: SDK MCP +
  cliente HTTP, sem Spring, sem risco de poluir stdout com logging de framework pesado.
- `back` passa a ser uma dependência de disponibilidade para o MCP funcionar — antes não
  era. Documentar isso no README como parte do fluxo de uso normal (deixar `back` rodando
  em background).
- `mcp/` fica desacoplado de qualquer mudança interna em `domain`/`infra` do backend —
  só quebra se o contrato da API REST mudar, o que é mais fácil de gerenciar
  (versionamento de API) do que acoplamento em código Java compartilhado.
- Segurança do header `X-Drum-Coach-Actor`: como o backend só deve aceitar conexões
  locais (loopback), não há necessidade de assinatura/criptografia no header — qualquer
  processo local já teria acesso equivalente ao arquivo SQLite diretamente. Se no futuro o
  backend for exposto além de localhost, essa decisão precisa ser revisitada.
