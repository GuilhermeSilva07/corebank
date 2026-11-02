# Proposal

## Why

A fase AUTH fechou, e hoje o sistema não tem nenhum registro de movimentação de dinheiro: o saldo existe só como um número em `accounts.balance`, e o endpoint de extrato (`AccountService.getStatement`) devolve uma lista vazia por falta de fonte de dados. O card `[TRANSFER] Design Transaction domain model` é o gargalo de toda a fase TRANSFER e, depois, de PIX, INVEST e LOAN. Este modelo precisa nascer já com o formato que essas fases vão precisar, pra que nenhuma delas exija outra migration na tabela.

## What Changes

- Nova tabela `transactions` (migration Flyway **V4**, versão reservada pelo time), com FK para `accounts`: um ledger append-only em que cada linha é uma perna de movimentação numa conta.
- Nova entidade JPA `Transaction` em `corebank-domain`, validada contra a migration por `ddl-auto=validate`, e o repositório correspondente.
- Três enums de domínio: `TransactionType` (DEBIT/CREDIT, a direção), `TransactionChannel` (TRANSFER/PIX/INVESTMENT/LOAN, a origem) e `TransactionStatus` (PENDING/COMPLETED/FAILED).
- `transfer_id` (uuid, sempre preenchido) agrupa as pernas de uma mesma operação: uma transferência entre contas gera duas linhas (DEBIT na origem, CREDIT no destino) com o mesmo `transfer_id`. Operações com uma conta só (aplicação em investimento, por exemplo) geram uma linha.
- `idempotency_key` (nullable) gravada só na perna que inicia a operação (o DEBIT), com unicidade por conta: `UNIQUE (account_id, idempotency_key)`.
- Só `status` pode mudar depois da criação. Todos os outros campos são imutáveis.
- Nenhum endpoint, nenhuma mudança de comportamento HTTP e nenhuma alteração em `Account`/`accounts`.

## Capabilities

### New Capabilities
- `transaction-ledger`: registro durável e auditável de cada movimentação de dinheiro numa conta (direção, canal, status, valor, saldo resultante), com agrupamento das pernas de uma mesma operação e proteção contra duplicidade por chave de idempotência.

### Modified Capabilities
(nenhuma: `jwt-authentication` não é afetada.)

## Impact

- `corebank-gateway`: nova migration `V4__create_transactions_table.sql` em `src/main/resources/db/migration/`. Só o gateway é dono do schema; o scheduler continua sem rodar migration.
- `corebank-domain`: nova entidade `Transaction`, enums `TransactionType`/`TransactionChannel`/`TransactionStatus`, `TransactionRepository`.
- Desbloqueia: `[TRANSFER] Internal transfer endpoint` (escreve as linhas), `[TRANSFER] Transaction history endpoint` (lê as linhas e alimenta o contrato `StatementItemResult` já existente, em que `balance_after` vira `runningBalance`), `[PIX] Idempotency key handling` e `[PIX] PIX transaction history` (filtra por `channel`), e os cards de INVEST/LOAN.
- Fora do escopo: locking de conta e cálculo do `balance_after` no momento da escrita (`[TRANSFER] Internal transfer endpoint`); tratamento de chave repetida com payload diferente (`[PIX] Idempotency key handling`); ligar o extrato aos dados reais (`[TRANSFER] Transaction history endpoint`); persistir tentativas falhadas.
