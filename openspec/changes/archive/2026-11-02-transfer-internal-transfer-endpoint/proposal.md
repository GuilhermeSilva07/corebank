# Proposal

## Why

O ledger `transactions` existe desde a V4, mas nada escreve nele: hoje não existe nenhuma forma de mover dinheiro entre contas. O card `[TRANSFER] Internal transfer endpoint (atomic debit/credit)` é o primeiro endpoint que movimenta saldo e é o núcleo que `[PIX] PIX transfer endpoint` e `[INVEST] Apply (invest) endpoint` vão reaproveitar. Por isso a fronteira transacional e o controle de concorrência precisam nascer certos aqui. O card `[TRANSFER] Insufficient balance validation` entra junto nesta change, por decisão do time: o próprio card diz que, na prática, é parte da mesma implementação, e sem ele o endpoint permitiria saldo negativo.

## What Changes

- Novo endpoint autenticado `POST /transfers` com corpo `{originAccountId, destinationAccountId, amount}`. Só o dono da conta de origem pode transferir.
- Numa única transação de banco: trava as duas contas, valida, debita a origem, credita o destino e grava as duas pernas no ledger (DEBIT na origem e CREDIT no destino, com o mesmo `transfer_id`, canal `TRANSFER`, status `COMPLETED` e `balance_after` de cada conta). As quatro escritas são confirmadas juntas ou nenhuma é.
- Lock pessimista (`SELECT ... FOR UPDATE`) nas linhas de `accounts`, adquirido sempre na mesma ordem para não haver deadlock. Não exige migration.
- Validação de saldo dentro da mesma transação e depois do lock: se o saldo da origem for menor que o valor, a transferência é rejeitada com `422 INSUFFICIENT_BALANCE` (card 16).
- Erros novos, todos pelo `GlobalExceptionHandler` no formato `{message, code, fieldErrors}`: autotransferência (`422`), conta de destino inexistente (`404`) e saldo insuficiente (`422`). Origem inexistente ou de outro usuário reaproveita o `403 ACCESS_DENIED` dos endpoints de saldo e extrato. Valor ausente, zero, negativo ou com mais de 2 casas decimais dá `400 VALIDATION_ERROR`.
- Operações de débito e crédito no domínio, reaproveitáveis por PIX e INVEST.
- Nenhuma mudança de schema, nenhuma migration e nenhuma alteração nos endpoints existentes.

## Capabilities

### New Capabilities
- `account-transfer`: mover dinheiro entre duas contas do banco de forma atômica, em nome do dono da conta de origem, com saldo validado e trilha no ledger.

### Modified Capabilities
(nenhuma: `transaction-ledger` já descreve as duas pernas de uma transferência e o status `COMPLETED` de movimentações síncronas; esta change só passa a escrever nele, sem mudar os requisitos. `jwt-authentication` não é afetada.)

## Impact

- `corebank-domain`: novo `TransferService` (`@Transactional`), `TransferCommand`, `TransferResult`, métodos de débito/crédito em `Account`, consulta com lock em `AccountRepository` e exceções novas (saldo insuficiente, autotransferência, destino inexistente).
- `corebank-gateway`: novo `TransferController`, DTOs `TransferRequest`/`TransferResponse` e novos handlers no `GlobalExceptionHandler`. O `SecurityConfig` não muda, porque `anyRequest().authenticated()` já protege a rota.
- Banco: nenhuma migration. As reservas V5 a V7 continuam livres.
- Desbloqueia: `[TRANSFER] Transaction history endpoint` (passa a ter dados reais), `[TRANSFER] Concurrency test` (card 18), `[PIX] PIX transfer endpoint` e `[INVEST] Apply (invest) endpoint`.
- Cards do board: esta change fecha os cards 15 e 16 juntos.
- Fora do escopo: idempotency key no `POST /transfers` (`[PIX] Idempotency key handling`); `description` na requisição; o teste de concorrência determinístico com N requisições simultâneas (card 18, embora o lock já entre aqui); limite/cheque especial; transferência para outros bancos; notificação ao cliente.
