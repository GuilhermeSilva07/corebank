# Tasks

## 1. Domínio: command e exceção (corebank-domain)

- [x] 1.1 Criar `InvalidFilterException` (`RuntimeException`) em `com.corebank.domain.exception`, no padrão de `InvalidPaginationException`. Verificar que `mvn -pl corebank-domain compile` passa
- [x] 1.2 Estender `GetStatementCommand` para `(accountId, requesterId, LocalDate from, LocalDate to, TransactionType type, page, size)`, com os três filtros anuláveis. O construtor compacto mantém a validação de paginação e passa a lançar `InvalidFilterException` quando `from` e `to` estão presentes e `from.isAfter(to)`. Adicionar o construtor secundário `(accountId, requesterId, page, size)` com os filtros nulos (design.md decisão 1). Verificar que o código existente compila sem alteração
- [x] 1.3 Ampliar `GetStatementCommandTest`: os casos atuais continuam passando; `from == to` é aceito; só `from` ou só `to` é aceito; `from` depois de `to` lança `InvalidFilterException`; o construtor de 4 argumentos deixa os filtros nulos. Verificar que `mvn -pl corebank-domain test` passa

## 2. Domínio: consulta do ledger (corebank-domain)

- [x] 2.1 Fazer `TransactionRepository` estender também `JpaSpecificationExecutor<Transaction>`. Criar `TransactionSpecifications` em `com.corebank.domain.repository` com predicados estáticos: `ofAccount(UUID)` por `account.id`, `completed()`, `createdAtFrom(Instant)` (`>=`), `createdAtBefore(Instant)` (`<`) e `ofType(TransactionType)` (design.md decisão 2). Verificar que compila
- [x] 2.2 Reescrever `AccountService.getStatement` como `@Transactional(readOnly = true)`, seguindo as decisões 3, 4 e 6 da design.md: `findOwnedAccount`, depois a `Specification` (conta + `COMPLETED` + só os filtros presentes, com `from`/`to` convertidos por `STATEMENT_ZONE = America/Sao_Paulo` em intervalo semiaberto), depois `PageRequest.of(page, size, Sort.by(desc("createdAt"), desc("id")))`, depois `findAll(spec, pageable)` e o mapeamento para `StatementItemResult(id, type.name(), amount, createdAt, balanceAfter)` e `StatementResult`. Injetar o `TransactionRepository`. Verificar que compila
- [x] 2.3 Atualizar `AccountServiceTest` (Mockito): substituir `getStatement_withValidOwner_returnsStatementStub` por testes que capturam a `Specification` e o `Pageable` passados ao repositório (`ArgumentCaptor`). Cobrir: ordenação `createdAt DESC, id DESC`; página e tamanho repassados; mapeamento de `Transaction` para `StatementItemResult` (`type.name()`, `balanceAfter` como `runningBalance`); totais copiados do `Page`; conversão de `from`/`to` para os instantes certos em São Paulo (`2026-11-02` dá `>= 2026-11-02T03:00Z` e `< 2026-11-03T03:00Z`); conta de outro usuário e conta inexistente lançam `AccountAccessDeniedException` sem consultar o `TransactionRepository`. A composição real dos filtros é provada contra o Postgres na 4.1. Verificar que `mvn -pl corebank-domain test` passa

## 3. Gateway: endpoint e erros (corebank-gateway)

- [x] 3.1 Em `AccountController`, extrair o mapeamento inline do `/statement` para um método privado `toResponse(StatementResult)` e adicionar `GET /{id}/transactions` com `from`/`to` (`@RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE) LocalDate`), `type` (`@RequestParam(required = false) TransactionType`) e `page`/`size` com os padrões 0/20. O endpoint monta o `GetStatementCommand` de 7 argumentos e reusa `toResponse`. Sem try/catch (design.md decisão 5). Verificar que `mvn -pl corebank-gateway compile` passa
- [x] 3.2 No `GlobalExceptionHandler`, adicionar o handler de `InvalidFilterException` (`400 INVALID_FILTER`, `message` do domain) e o de `MethodArgumentTypeMismatchException` (`400`, `fieldErrors = [{field: ex.getName(), message}]`, com código `INVALID_PAGINATION` quando o parâmetro é `page`/`size` e `INVALID_FILTER` nos outros casos), no formato `{message, code, fieldErrors}` (design.md decisão 5). Verificar que compila e que os testes de integração existentes continuam passando (`mvn -pl corebank-gateway test`)

## 4. Testes de integração (corebank-gateway)

- [x] 4.1 Criar `AccountTransactionHistoryIntegrationTest` (`@SpringBootTest`, Postgres real, MockMvc + `springSecurity()`, no padrão de `AccountBalanceIntegrationTest`). Inserir linhas do ledger via `JdbcTemplate` com `created_at` explícito (design.md, Risks) e cobrir os cenários da spec `account-statement`: sem token → 401; conta de outro usuário e conta inexistente → o mesmo 403 `ACCESS_DENIED`; conta sem movimentos → lista vazia; só lançamentos da própria conta; `PENDING`/`FAILED` ocultos e fora do `totalElements`; ordem do mais novo para o mais antigo; timestamps iguais paginados sem repetir nem pular; página 1 com 25 linhas e `size` 20 → 5 itens e `totalPages` 2; página além da última → lista vazia; `size` 0/101 e `page` -1 → 400 `INVALID_PAGINATION`; `from`/`to` inclusivos, incluindo 23:59 do último dia; fronteira em São Paulo (01:00Z de 3/11 aparece em `from=to=2026-11-02`); só `from` e só `to`; `from` depois de `to` → 400 `INVALID_FILTER`; `type=DEBIT`; `type=FOO` e `from=01/11/2026` → 400 `INVALID_FILTER` nomeando o parâmetro; período + tipo combinados com `totalElements` correto. Verificar que `mvn -pl corebank-gateway test` passa
- [x] 4.2 No mesmo teste, cobrir o contrato compartilhado: depois de uma transferência real via `POST /transfers`, `/statement` lista o DEBIT (não é mais vazio), e `/statement` e `/transactions` sem filtro, com a mesma página e o mesmo tamanho, devolvem corpos iguais. Verificar que passa
- [x] 4.3 Rodar os testes que já usam `/statement` (`JwtAuthenticationFilterIntegrationTest`, `TokenAndProtectedEndpointIntegrationTest`) e confirmar que continuam verdes sem alteração. Se algum depender da lista vazia, ajustar a asserção e registrar o motivo no PR

## 5. Integração final

- [x] 5.1 Rodar `mvn spotless:apply` e `mvn clean verify` na raiz e verificar build verde em todos os módulos (domain, gateway, scheduler)

## Workflow follow-up

- O card `[TRANSFER] Transaction history endpoint` está atribuído ao Lucas Kevin. Quem implementar escreve os testes unitários do próprio código (regra 6).
- Abrir PR de `feature/transaction-history-endpoint` contra `develop`, citando o card 17 e mencionando que o `/statement` deixou de ser stub (cumpre o contrato do `[AUTH] Account statement endpoint`).
- Combinar a ordem de merge com o card `[PLATFORM] API documentation with OpenAPI/Swagger`, que também mexe no `AccountController`.
- Pedir review de outra pessoa antes do merge (regra 7).
- Rodar `/opsx:archive` depois que o PR for revisado e mergeado.
