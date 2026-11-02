# Tasks

## 1. Schema (corebank-gateway)

- [x] 1.1 Criar `corebank-gateway/src/main/resources/db/migration/V4__create_transactions_table.sql` com a tabela `transactions` (`id uuid PK`, `transfer_id uuid NOT NULL`, `account_id uuid NOT NULL REFERENCES accounts(id)`, `type`/`channel`/`status varchar(255) NOT NULL`, `amount numeric(15,2) NOT NULL CHECK (amount > 0)`, `balance_after numeric(15,2) NOT NULL`, `description varchar(255)`, `idempotency_key varchar(255)`, `created_at timestamptz NOT NULL`), `UNIQUE (account_id, idempotency_key)`, índice `(account_id, created_at DESC)` e índice `(transfer_id)`, conforme design.md decisões 1 a 4 e 8. Verificar que nenhuma outra V4 existe em `develop` e que `mvn -pl corebank-gateway test -Dtest=CorebankGatewayApplicationTests` sobe com a migration aplicada

## 2. Domínio (corebank-domain)

- [x] 2.1 Criar os enums `TransactionType` (DEBIT, CREDIT), `TransactionChannel` (TRANSFER, PIX, INVESTMENT, LOAN) e `TransactionStatus` (PENDING, COMPLETED, FAILED) em `com.corebank.domain.entity` (ou num subpacote `enums`, se preferir) e verificar que `mvn -pl corebank-domain compile` passa
- [x] 2.2 Criar a entidade `Transaction` mapeando a tabela `transactions`: `@Getter` na classe, sem `@Setter` de classe, `@NoArgsConstructor(access = PROTECTED)`, `@ManyToOne(fetch = LAZY)` para `Account`, `@Enumerated(STRING)` nos três enums, `@Column(updatable = false)` em todas as colunas exceto `status`, criação via construtor ou factory (`transferId`, `account`, `type`, `channel`, `amount`, `balanceAfter`, `description`, `idempotencyKey`; `status` e `createdAt` definidos na criação) e métodos `markCompleted()`/`markFailed()`, conforme design.md decisões 5 a 7. Verificar que `mvn -pl corebank-domain compile` passa
- [x] 2.3 Criar `TransactionRepository extends JpaRepository<Transaction, UUID>` em `com.corebank.domain.repository`, sem queries customizadas (elas ficam com `[TRANSFER] Transaction history endpoint`), e verificar que compila
- [x] 2.4 Criar `TransactionTest` (unitário, em `corebank-domain/src/test/.../entity/`) cobrindo: a criação preenche `createdAt` e o status inicial; `markCompleted()`/`markFailed()` mudam só o status; não existe setter público para os demais campos (verificação por compilação/reflexão, à escolha). Verificar que `mvn -pl corebank-domain test` passa

## 3. Verificação do mapeamento e das constraints (corebank-gateway)

- [x] 3.1 Criar `TransactionPersistenceIntegrationTest` (`@SpringBootTest`, Postgres real, no padrão de `AccountBalanceIntegrationTest`) cobrindo os cenários da spec `transaction-ledger`: o contexto sobe com `ddl-auto=validate` (o mapeamento bate com a V4); salvar DEBIT + CREDIT com o mesmo `transferId` em duas contas e reler os dois pelo `transferId`; `amount = 0` e `amount < 0` violam a constraint; `account_id` inexistente viola a FK; chave de idempotência repetida na mesma conta viola a UNIQUE; a mesma chave em contas diferentes passa; várias linhas sem chave na mesma conta passam; `description` nula passa; `markCompleted()` persiste o status sem alterar os outros campos. Verificar que `mvn -pl corebank-gateway test` passa

## 4. Integração final

- [x] 4.1 Rodar `mvn spotless:apply` e `mvn clean verify` na raiz e verificar build verde em todos os módulos (domain, gateway, scheduler), confirmando que o scheduler continua sem rodar migration

## Workflow follow-up

- Abrir PR de `feature/transfer-transaction-domain-model` contra `develop`. É prioridade: o card é gargalo de TRANSFER, PIX, INVEST e LOAN.
- Pedir review de outra pessoa antes do merge (nenhum PR se auto-aprova). O card só vai para Done depois de Code Review e QA/Testing.
- Depois do merge, avisar o time que `[TRANSFER] Internal transfer endpoint` e `[TRANSFER] Transaction history endpoint` estão desbloqueados e podem andar em paralelo.
- Rodar `/opsx:archive` depois que o PR for revisado e mergeado.
