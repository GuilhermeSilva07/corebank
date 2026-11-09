# Proposal

## Why

Desde a change `transfer-internal-transfer-endpoint` o ledger `transactions` recebe linhas reais, mas nenhum endpoint as expõe: o `GET /accounts/{id}/statement` (card `[AUTH] Account statement endpoint`) foi entregue como stub e sempre devolve lista vazia, porque o `Transaction` ainda não existia. O card `[TRANSFER] Transaction history endpoint` fecha essa lacuna e é um dos dois cards que faltam para fechar a fase TRANSFER.

## What Changes

- Novo endpoint autenticado `GET /accounts/{id}/transactions`, só para o dono da conta, com filtros opcionais que podem ser combinados:
  - `from` e `to`: datas `YYYY-MM-DD`, inclusivas, interpretadas no fuso `America/Sao_Paulo`.
  - `type`: `DEBIT` ou `CREDIT`.
  - `page` e `size`: mesmas regras e valores padrão do `/statement` (0 e 20; `size` entre 1 e 100).
- Resultados ordenados do mais recente para o mais antigo, com desempate estável para que a paginação não repita nem pule linhas.
- Resposta com exatamente o mesmo formato do `/statement` (`StatementResponse`: `items[{id, type, amount, timestamp, runningBalance}]`, `page`, `size`, `totalElements`, `totalPages`). Nenhum formato novo.
- `GET /accounts/{id}/statement` deixa de ser stub: passa a devolver os lançamentos reais da conta, como um `/transactions` sem filtros. O contrato não muda; só deixa de ser sempre vazio.
- Só lançamentos `COMPLETED` aparecem no extrato, porque o formato da resposta não tem campo de status e o `runningBalance` só faz sentido para movimentações efetivadas.
- Filtro inválido (`type` desconhecido, data mal formatada, `from` depois de `to`) gera `400` no formato padrão `{message, code, fieldErrors}`, com o código novo `INVALID_FILTER`, pelo `GlobalExceptionHandler`. Hoje um parâmetro de tipo errado nem passa pelo handler.
- Conta inexistente ou de outro usuário: o mesmo `403 ACCESS_DENIED` do saldo e do extrato.
- Nenhuma migration: o índice `idx_transactions_account_created_at (account_id, created_at DESC)` da V4 já atende a consulta.

## Capabilities

### New Capabilities
- `account-statement`: consulta paginada dos lançamentos do ledger de uma conta pelo dono dela, com filtros por período e por tipo, servida pelos endpoints `/statement` e `/transactions` com o mesmo formato de resposta.

### Modified Capabilities
(nenhuma: `transaction-ledger` descreve como os lançamentos são gravados e não muda; esta change só passa a lê-los. `account-transfer` e `jwt-authentication` não são afetadas.)

## Impact

- `corebank-domain`: `GetStatementCommand` ganha os filtros opcionais (`from`, `to`, `type`) e passa a validar o período; `AccountService.getStatement` passa a consultar o ledger de verdade (`@Transactional(readOnly = true)`); `TransactionRepository` ganha a consulta paginada e filtrada; nova exceção `InvalidFilterException`.
- `corebank-gateway`: `AccountController` ganha o `GET /{id}/transactions`, e o `/statement` passa a usar a mesma consulta e o mesmo mapeamento; o `GlobalExceptionHandler` ganha os handlers de `InvalidFilterException` e de parâmetro de query com tipo inválido. O `SecurityConfig` não muda, porque `anyRequest().authenticated()` já protege a rota.
- Testes existentes afetados: `AccountServiceTest.getStatement_withValidOwner_returnsStatementStub` deixa de valer como está, porque o extrato não é mais stub.
- Banco: nenhuma migration.
- Risco de conflito: o card `[PLATFORM] API documentation with OpenAPI/Swagger` (In Progress) pode mexer no `AccountController` ao mesmo tempo.
- Cards do board: fecha `[TRANSFER] Transaction history endpoint` e cumpre o contrato deixado pelo `[AUTH] Account statement endpoint`.
- Fora do escopo: filtro por canal (TRANSFER/PIX/...); expor `channel`, `description` ou a contraparte no item do extrato (mudaria o formato da resposta); exportar o extrato em arquivo (ideia de módulo de arquivos, v2); paginação por cursor; o histórico próprio de PIX (`[PIX] PIX transaction history`).
