# Design

## Context

Motivação em proposal.md (Why). Requisitos em `specs/account-statement/spec.md`. Estado atual relevante:

- `AccountService.getStatement(GetStatementCommand)` só chama `findOwnedAccount` e devolve `new StatementResult(List.of(), page, size, 0L, 0)`. Não tem `@Transactional`.
- `GetStatementCommand(accountId, requesterId, page, size)` valida a paginação no construtor compacto e lança `InvalidPaginationException`, que vira `400 INVALID_PAGINATION` no `GlobalExceptionHandler`.
- `StatementItemResult(id, type, amount, timestamp, runningBalance)`: `type` é `String` ("DEBIT"/"CREDIT"). Os DTOs `StatementResponse` e `StatementItemResponse` do gateway espelham os results. Esse é o contrato que o card manda reaproveitar.
- `Transaction` tem `account` (LAZY), `type` (`TransactionType`), `status`, `amount`, `balanceAfter` e `createdAt` (`Instant`, `timestamptz`). `TransactionRepository` é um `JpaRepository` vazio.
- A V4 já tem `idx_transactions_account_created_at (account_id, created_at DESC)`.
- O `GlobalExceptionHandler` não trata `MethodArgumentTypeMismatchException`. Hoje um `@RequestParam` com tipo errado (por exemplo `page=abc`) cai no tratamento padrão do Spring e não sai no formato `{message, code, fieldErrors}`.
- Hoje só existem lançamentos `COMPLETED`, porque transferências são síncronas. `PENDING`/`FAILED` vão aparecer com PIX.

## Goals / Non-Goals

**Goals:**
- Uma única consulta no domain que serve `/statement` (sem filtros) e `/transactions` (com filtros), com um único mapeamento para o contrato existente.
- Paginação estável (sem repetir nem pular linhas) usando o índice que já existe.
- Fronteira de dia previsível para o cliente (fuso de São Paulo), sem depender do fuso da JVM ou do banco.

**Non-Goals:**
- Fuso configurável por usuário ou por requisição.
- Expor status, canal ou descrição no item. Isso exigiria um formato novo, e o card proíbe.
- Otimizar o `count` da paginação: o volume de um projeto de estudo não justifica.

## Decisions

### 1. Estender `GetStatementCommand` em vez de criar um command novo
O record passa a ser `GetStatementCommand(accountId, requesterId, from, to, type, page, size)`, com `from`/`to` (`LocalDate`) e `type` (`TransactionType`) anuláveis. Um construtor secundário `(accountId, requesterId, page, size)` mantém a chamada atual do `/statement` e os testes existentes compilando.

O construtor compacto também passa a validar `from` depois de `to`, lançando a nova `InvalidFilterException` (domain, `RuntimeException`, no padrão de `InvalidPaginationException`).

- **Alternativa: `GetTransactionHistoryCommand` separado.** Teria dois commands com os mesmos campos de paginação e duas validações iguais para o mesmo resultado. A spec diz que o statement é o histórico sem filtro, e o modelo deve refletir isso.
- O command usa o enum `TransactionType` do domain, não `String`. A conversão de `"DEBIT"` para enum acontece no gateway (decisão 5), então o domain nunca recebe um tipo inválido.

### 2. Consulta com `JpaSpecificationExecutor`, não `@Query` com parâmetros anuláveis
`TransactionRepository` passa a estender também `JpaSpecificationExecutor<Transaction>`. O service monta a `Specification` a partir de predicados pequenos (conta, status `COMPLETED`, `createdAt >= início`, `createdAt < fim`, tipo) e só inclui os filtros presentes.

- **Alternativa: `@Query("... where (:type is null or t.type = :type) and (:from is null or t.createdAt >= :from) ...")`.** No PostgreSQL, um parâmetro `null` sem tipo em `:from is null` costuma falhar com `could not determine data type of parameter`. Também gera um plano de execução pior, já que o banco não sabe quais filtros estão ativos.
- **Alternativa: um método derivado por combinação de filtros** (`findByAccountIdAndTypeAndCreatedAtBetween...`). Dariam quatro ou mais métodos, e cada filtro novo dobraria esse número.
- Os predicados ficam numa classe `TransactionSpecifications` (métodos estáticos) em `com.corebank.domain.repository`, para poderem ser testados sem banco.

### 3. Ordenação `created_at DESC, id DESC`
O `Pageable` é montado no service com `Sort.by(desc("createdAt"), desc("id"))`. Só `createdAt` não basta: duas linhas com o mesmo timestamp podem trocar de lugar entre uma página e outra, e o desempate por `id` torna a ordem total e estável (spec: "Newest entries come first with a stable order"). O prefixo `(account_id, created_at DESC)` do índice da V4 continua sendo usado. O desempate só pesa entre linhas empatadas, então não precisa de índice novo nem de migration.

- O cliente não escolhe a ordenação (não há `sort` na query string). Isso evita ordenar por coluna sem índice e mantém a resposta igual entre `/statement` e `/transactions`.

### 4. Datas `LocalDate` convertidas para um intervalo semiaberto em `America/Sao_Paulo`
O domain define `ZoneId STATEMENT_ZONE = ZoneId.of("America/Sao_Paulo")` como constante no `AccountService`. A conversão é:
- `from` vira `createdAt >= from.atStartOfDay(zone).toInstant()`
- `to` vira `createdAt < to.plusDays(1).atStartOfDay(zone).toInstant()`

O intervalo semiaberto resolve o "inclusivo" sem depender de `23:59:59.999999`. `atStartOfDay(zone)` trata corretamente dias com transição de horário de verão, caso o Brasil volte a adotá-lo.

- **Alternativa: usar o fuso da JVM (`ZoneId.systemDefault()`).** O resultado mudaria entre a máquina de dev, o CI e o Docker. Foi descartada.
- A constante fica no domain e não em `application.properties`, porque é regra de negócio do extrato. Torná-la configurável fica para quando houver necessidade.

### 5. Gateway: conversão dos parâmetros e erros de filtro
`AccountController` ganha:
- `GET /{id}/transactions` com `@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate from`, idem `to`, `@RequestParam(required = false) TransactionType type`, mais `page`/`size` com os mesmos padrões do `/statement`.
- Um método privado `toResponse(StatementResult)`, usado pelos dois endpoints, com o mapeamento que hoje está inline no `/statement`.

O `TransactionType` do domain entra direto no `@RequestParam` porque o Spring converte o enum sozinho. É um parâmetro de query, não um DTO de corpo, então isso não fere a regra 2 (DTO → Command): o controller continua só montando o command.

No `GlobalExceptionHandler`:
- `InvalidFilterException` vira `400 INVALID_FILTER` com a mensagem do domain.
- `MethodArgumentTypeMismatchException` vira `400` no formato padrão, com `fieldErrors = [{field: <nome do parâmetro>, message}]`. O código depende do parâmetro: `INVALID_FILTER` para `from`/`to`/`type` e `INVALID_PAGINATION` para `page`/`size`. Assim `page=abc` também deixa de escapar do formato padrão. É um ganho colateral pequeno, e os códigos continuam coerentes com os que já existem.

### 6. Mapeamento e transação no service
`getStatement` passa a ser `@Transactional(readOnly = true)`. Ele valida o dono com `findOwnedAccount` (a mesma regra de 403 de hoje), executa `findAll(spec, pageable)` e mapeia cada `Transaction` para `StatementItemResult(id, type.name(), amount, createdAt, balanceAfter)`. Depois monta o `StatementResult` a partir do `Page` (`getNumber`, `getSize`, `getTotalElements`, `getTotalPages`).

- O mapeamento não toca `transaction.getAccount()` (LAZY), então não há N+1.
- O filtro por conta usa `account.id` na `Specification`. O Hibernate resolve isso pela FK, sem join em `accounts`.

## Risks / Trade-offs

- [Conflito com o card de Swagger em andamento, que pode anotar o `AccountController`] → combinar a ordem dos merges com quem está no card. Esta change só adiciona um método e extrai o `toResponse`, o que facilita o rebase.
- [Esconder `PENDING`/`FAILED` pode confundir quando o PIX assíncrono chegar ("mandei um PIX e ele não aparece")] → decisão explícita da spec. O card `[PIX] PIX transaction history` revisita o assunto, e a mudança fica isolada num único predicado.
- [Sem limite de tamanho do período, um cliente pode pedir anos de extrato] → a paginação (máximo de 100 itens por página) limita cada resposta. O `count` sobre um período grande é aceitável no volume do projeto.
- [Os testes de fronteira de dia dependem de `createdAt`, que vem de `Instant.now()` no construtor de `Transaction`] → os testes de integração inserem linhas via `JdbcTemplate` com `created_at` explícito, sem mudar a entidade nem adicionar um `Clock` só para teste.
- [`AccountServiceTest.getStatement_withValidOwner_returnsStatementStub` passa a falhar] → é esperado. O teste é reescrito para o comportamento real (tasks.md).

## Migration Plan

Nenhuma migration nem configuração nova. O `/statement` muda de "sempre vazio" para "lançamentos reais" sem mudar o formato, então clientes existentes não quebram. O rollback é reverter o PR.
