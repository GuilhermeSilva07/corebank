# Design

## Context

Motivação em proposal.md (Why). Requisitos em `specs/account-transfer/spec.md`. Estado atual relevante:

- `Account` (`accounts`) tem `balance numeric(15,2)`, `@Getter @Setter` de classe e **não** tem `@Version`. `AccountRepository` é um `JpaRepository` sem queries customizadas.
- `AccountService.findOwnedAccount(accountId, requesterId)` lança `AccountAccessDeniedException` (→ `403 ACCESS_DENIED`) tanto para conta inexistente quanto para conta de outro usuário. Os endpoints de saldo e extrato já seguem essa regra.
- `Transaction` é imutável exceto o status. O construtor recebe `transferId, account, type, channel, status, amount, balanceAfter, description, idempotencyKey`. O `transferId` é gerado pela aplicação. A V4 tem `CHECK (amount > 0)` e `UNIQUE (account_id, idempotency_key)`.
- O usuário autenticado chega no controller como `Principal`, com `getName()` = UUID do usuário (padrão de `AccountController`).
- `@Transactional` já é usado em services do domain (`UserRegistrationService`, `RefreshTokenService`). O lock otimista só existe em `RefreshToken` (V3 adicionou `version`).
- Versões de migration reservadas: V5 `pix_keys`, V6 investment, V7 loan.

## Goals / Non-Goals

**Goals:**
- Uma transferência é tudo-ou-nada: 2 saldos + 2 linhas no ledger na mesma transação.
- Verificação de saldo e débito sem janela de corrida entre requisições simultâneas.
- Lógica de débito/crédito reaproveitável por PIX e INVEST sem duplicação.

**Non-Goals:**
- Idempotency key no `POST /transfers` (`[PIX] Idempotency key handling`). As linhas gravam `idempotency_key = NULL`.
- Teste de concorrência determinístico com N threads e prova "falha antes/passa depois" (card 18). O lock entra aqui; o card 18 prova que ele funciona sob carga.
- `description` na requisição (a coluna fica `NULL`), limite/cheque especial, transferência entre bancos.

## Decisions

### 1. Lock pessimista (`PESSIMISTIC_WRITE`) nas duas contas, em ordem fixa
Alternativas consideradas:
- **(A) Pessimista, `SELECT ... FOR UPDATE` (escolhida):** a segunda transação que toca a mesma conta espera a primeira terminar e então lê o saldo já atualizado. Não precisa de retry nem de migration.
- **(B) Otimista com `@Version` + retry:** precisaria de uma migration adicionando `version` em `accounts`, que colide com as reservas V5–V7 e exigiria renegociar a numeração. Sob disputa pela mesma conta (o cenário do card 18), gera conflito e retry em cascata. Ainda teria que garantir que o retry não duplique a transferência.
- **(C) `UPDATE accounts SET balance = balance - :amount WHERE id = :id AND balance >= :amount`:** é atômico e rápido, mas tira a regra de saldo do domínio (vira SQL) e não devolve o `balance_after` sem uma segunda leitura. Também dificulta o reaproveitamento por INVEST.

Implementação:
- `AccountRepository.findByIdForUpdate(UUID id)` com `@Lock(LockModeType.PESSIMISTIC_WRITE)` e `@Query("select a from Account a where a.id = :id")`, retornando `Optional<Account>`.
- **Ordem do lock:** as duas contas são travadas em ordem crescente de id (`UUID.compareTo`), qualquer que seja o sentido da transferência. Assim A→B e B→A simultâneas pedem os locks na mesma ordem e uma espera a outra, em vez de entrar em deadlock. A ordem só precisa ser consistente entre os chamadores Java, já que todo débito/crédito passa por este service. Isso fica documentado no javadoc para PIX/INVEST seguirem o mesmo caminho.
- O `@ManyToOne` `user` em `Account` é EAGER, mas numa query JPQL sem `join fetch` o Hibernate carrega o `User` num select separado, sem lock. O `FOR UPDATE` fica só em `accounts`. Isso é verificado pelo teste de integração (Postgres recusa `FOR UPDATE` no lado nulo de um outer join, então um join inesperado apareceria como erro).

### 2. Ordem das validações dentro do service
1. **Autotransferência** (`origin == destination`): rejeita antes de qualquer acesso ao banco.
2. **Lock** das duas contas na ordem da decisão 1.
3. **Origem** inexistente ou de outro usuário → `AccountAccessDeniedException` (mesmo `403` de saldo/extrato). A verificação de dono acontece depois do lock, sobre a linha travada.
4. **Destino** inexistente → `DestinationAccountNotFoundException` (`404`).
5. **Saldo** (`origin.debit(amount)`) → `InsufficientBalanceException` (`422`), com a diferença na mensagem.
6. Crédito no destino, `UUID transferId = UUID.randomUUID()`, duas `Transaction` (`TRANSFER`, `COMPLETED`, `balance_after` de cada conta) e `saveAll`.

Validar a origem antes do destino evita que alguém sem dono da origem descubra se um id de destino existe (resposta sempre 403). O `404` do destino só é visível ao dono legítimo de uma conta. Os ids são UUID aleatórios, então o risco de enumeração é baixo. A regra 9 do projeto (enumeração) vale para endpoints de auth, e aqui não se aplica do mesmo jeito.

### 3. Débito e crédito como métodos de `Account`
- `Account.debit(BigDecimal amount)` lança `InsufficientBalanceException` se `balance < amount` e subtrai. `Account.credit(BigDecimal amount)` soma. Ambos rejeitam `amount <= 0` com `IllegalArgumentException` (defesa em profundidade; a validação de entrada já barrou isso antes).
- Motivo: `[INVEST] Apply (invest) endpoint` precisa da mesma checagem de saldo ("reuse, don't reimplement"). Com a regra na entidade, qualquer service que travar a conta reaproveita o mesmo método.
- `@Setter` de classe continua em `Account` (testes e o registro de usuário usam `setBalance`). Remover fica fora do escopo. O service de transferência usa só `debit`/`credit`.
- A exceção de saldo carrega `shortfall` (`amount - balance`) para a mensagem `Insufficient balance: missing 20.00`. Isso só revela o saldo da própria conta ao próprio dono.

### 4. Command, Result e fronteira com o gateway (regra 2)
- `TransferCommand(UUID requesterId, UUID originAccountId, UUID destinationAccountId, BigDecimal amount)`: record sem anotações de validação. O construtor compacto rejeita nulos e `amount <= 0` ou escala > 2 com `IllegalArgumentException`, como invariante de domínio. O caminho normal de erro de entrada é o `@Valid` do DTO.
- `TransferResult(UUID transferId, UUID originAccountId, UUID destinationAccountId, BigDecimal amount, BigDecimal originBalanceAfter, Instant createdAt)`.
- `TransferRequest` (gateway): `@NotNull` nos três campos, `@Positive` e `@Digits(integer = 13, fraction = 2)` em `amount`. Lombok com `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` (regra 4). `13 + 2 = 15` dígitos, igual a `numeric(15,2)`.
- `TransferResponse` (gateway): espelha `TransferResult`. O controller devolve `201 Created`, sem header `Location`, porque não existe `GET /transfers/{id}`.
- `TransferController` fica em `@RequestMapping("/transfers")`, faz `UUID.fromString(principal.getName())`, monta o command, chama o service e mapeia o result. Nenhum try/catch (regra 5).

### 5. Fronteira transacional
- `TransferService.transfer(TransferCommand)` é `@Transactional` (padrão `READ_COMMITTED` do Postgres; o lock da decisão 1 é que garante a serialização por conta).
- As exceções de negócio são `RuntimeException`, então o rollback é automático. Como todas as validações (passos 1 a 5) vêm antes de qualquer escrita, uma rejeição normal não deixa nada para desfazer. O rollback importa para falhas inesperadas depois do débito (por exemplo, erro ao gravar uma linha do ledger), que é o cenário "no partial state" da spec.
- Sem `REQUIRES_NEW` e sem gravar linha `FAILED`: segue a decisão do design anterior (falha síncrona não persiste nada).

### 6. Mapeamento de erros (`GlobalExceptionHandler`)
| Exceção (domain) | Status | `code` |
|---|---|---|
| `MethodArgumentNotValidException` (já existe) | 400 | `VALIDATION_ERROR` |
| `AccountAccessDeniedException` (já existe) | 403 | `ACCESS_DENIED` |
| `DestinationAccountNotFoundException` | 404 | `DESTINATION_ACCOUNT_NOT_FOUND` |
| `SelfTransferNotAllowedException` | 422 | `SELF_TRANSFER_NOT_ALLOWED` |
| `InsufficientBalanceException` | 422 | `INSUFFICIENT_BALANCE` (mensagem com a diferença) |

`401` sem token continua vindo do `RestAuthenticationEntryPoint`. Corpo JSON malformado ou UUID inválido no corpo vira `HttpMessageNotReadableException`. Hoje isso não tem handler e cai no padrão do Spring (400). Não criamos handler novo para ele aqui, para manter o escopo. Fica anotado como risco.

## Risks / Trade-offs

- [Lock pessimista serializa transferências da mesma conta; uma conta muito disputada vira gargalo] → Aceitável para o volume de um projeto de estudo. As transações são curtas (2 selects, 2 updates, 2 inserts).
- [O Postgres espera o lock indefinidamente por padrão; uma transação travada segura a outra] → As transações são curtas e não há chamada externa dentro delas. Um `lock_timeout` pode ser adicionado depois, se o card 18 mostrar necessidade.
- [Deadlock se algum código futuro travar contas em outra ordem] → Ordem documentada no javadoc de `findByIdForUpdate` e do service. PIX e INVEST devem delegar ao service em vez de travar contas por conta própria.
- [Estouro de `numeric(15,2)` no saldo de destino gera erro de banco (500), não um 4xx] → Só com saldos acima de 10^13, irreal no simulador. Não tratado.
- [`HttpMessageNotReadableException` sem formato `{message, code, fieldErrors}`] → Fora do escopo; pode virar card de `[PLATFORM]`.
- [O teste de "falha no meio" precisa forçar um erro depois do débito] → No teste de integração, usar `@MockitoSpyBean` no `TransactionRepository` para lançar exceção no `saveAll` e verificar que os saldos não mudaram.

## Migration Plan

- Sem migration. Deploy direto: o endpoint é novo e nenhum contrato existente muda.
- Rollback: reverter o PR. Linhas já gravadas no ledger por transferências reais permanecem válidas (o modelo é append-only e consistente com os saldos).
