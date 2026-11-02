# Tasks

## 1. Domínio: contas (corebank-domain)

- [x] 1.1 Criar as exceções `InsufficientBalanceException` (carrega `shortfall` e monta a mensagem `Insufficient balance: missing <valor>`), `SelfTransferNotAllowedException` e `DestinationAccountNotFoundException` em `com.corebank.domain.exception`, todas `RuntimeException`, no padrão de `AccountAccessDeniedException`. Verificar que `mvn -pl corebank-domain compile` passa
- [x] 1.2 Adicionar `debit(BigDecimal)` e `credit(BigDecimal)` em `Account`: ambos rejeitam `amount <= 0` com `IllegalArgumentException`; `debit` lança `InsufficientBalanceException` quando `balance < amount` e não altera o saldo nesse caso (design.md decisão 3). Verificar que compila
- [x] 1.3 Adicionar `Optional<Account> findByIdForUpdate(UUID id)` em `AccountRepository` com `@Lock(LockModeType.PESSIMISTIC_WRITE)` e `@Query("select a from Account a where a.id = :id")`, com javadoc explicando que os locks devem ser pedidos em ordem crescente de id (design.md decisão 1). Verificar que compila
- [x] 1.4 Criar `AccountTest` (unitário) para `debit`/`credit`: débito com saldo suficiente, débito de exatamente o saldo deixa 0.00, débito acima do saldo lança `InsufficientBalanceException` com o `shortfall` correto e não altera o saldo, crédito soma, valor zero ou negativo lança `IllegalArgumentException`. Verificar que `mvn -pl corebank-domain test` passa

## 2. Domínio: transferência (corebank-domain)

- [x] 2.1 Criar `TransferCommand(requesterId, originAccountId, destinationAccountId, amount)` em `com.corebank.domain.command`: record sem anotações de validação, construtor compacto rejeitando nulos, `amount <= 0` e escala > 2 com `IllegalArgumentException` (design.md decisão 4)
- [x] 2.2 Criar `TransferResult(transferId, originAccountId, destinationAccountId, amount, originBalanceAfter, createdAt)` em `com.corebank.domain.result`
- [x] 2.3 Criar `TransferService` (`@Service`, `@Transactional` em `transfer(TransferCommand)`) seguindo exatamente a ordem da design.md decisão 2: autotransferência → lock das duas contas em ordem crescente de id via `findByIdForUpdate` → origem inexistente ou de outro usuário (`AccountAccessDeniedException`) → destino inexistente (`DestinationAccountNotFoundException`) → `origin.debit` → `destination.credit` → duas `Transaction` (DEBIT/CREDIT, mesmo `transferId` gerado com `UUID.randomUUID()`, canal `TRANSFER`, status `COMPLETED`, `balance_after` de cada conta, `description` e `idempotencyKey` nulos) gravadas com `transactionRepository.saveAll`. Retorna `TransferResult`. Verificar que compila
- [x] 2.4 Criar `TransferCommandTest` (unitário): command válido é criado; nulos, zero, negativo e `10.005` lançam `IllegalArgumentException`
- [x] 2.5 Criar `TransferServiceTest` (Mockito, no padrão de `AccountServiceTest`) cobrindo: sucesso debita a origem, credita o destino e grava duas linhas com o mesmo `transferId`, tipos DEBIT/CREDIT, canal `TRANSFER`, status `COMPLETED` e `balance_after` corretos; autotransferência rejeitada sem chamar o repositório; origem de outro usuário e origem inexistente lançam `AccountAccessDeniedException`; destino inexistente lança `DestinationAccountNotFoundException` sem gravar linhas; saldo insuficiente lança `InsufficientBalanceException` sem gravar linhas; os locks são pedidos em ordem crescente de id nos dois sentidos (A→B e B→A), verificado com `InOrder`. Verificar que `mvn -pl corebank-domain test` passa

## 3. Gateway: endpoint (corebank-gateway)

- [x] 3.1 Criar `TransferRequest` (`@NotNull` nos três campos; `@Positive` e `@Digits(integer = 13, fraction = 2)` em `amount`; Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor`, sem `@Data`) e `TransferResponse` (espelha `TransferResult`) em `com.corebank.gateway.dto`
- [x] 3.2 Criar `TransferController` em `@RequestMapping("/transfers")` com `@PostMapping` recebendo `@Valid @RequestBody TransferRequest` e `Principal`: monta `TransferCommand` com `UUID.fromString(principal.getName())`, chama `TransferService.transfer`, mapeia para `TransferResponse` e devolve `201 Created`. Sem try/catch (design.md decisão 4)
- [x] 3.3 Adicionar ao `GlobalExceptionHandler` os handlers de `DestinationAccountNotFoundException` (404 `DESTINATION_ACCOUNT_NOT_FOUND`), `SelfTransferNotAllowedException` (422 `SELF_TRANSFER_NOT_ALLOWED`) e `InsufficientBalanceException` (422 `INSUFFICIENT_BALANCE`, `message` com a diferença), no formato `{message, code, fieldErrors}` (design.md decisão 6). Verificar que `mvn -pl corebank-gateway compile` passa

## 4. Testes de integração (corebank-gateway)

- [x] 4.1 Criar `TransferIntegrationTest` (`@SpringBootTest`, Postgres real, MockMvc + `springSecurity()`, no padrão de `AccountBalanceIntegrationTest`) cobrindo os cenários da spec `account-transfer`: sucesso (201, saldos finais corretos, exatamente 2 linhas no ledger com o mesmo `transferId` e `balance_after` corretos, corpo da resposta); transferência do saldo inteiro deixa 0.00; sem token → 401; origem de outro usuário e origem inexistente → o mesmo 403 `ACCESS_DENIED`; destino inexistente → 404 sem alterar o saldo nem gravar linhas; autotransferência → 422; saldo insuficiente → 422 com a diferença na mensagem, sem alterar saldos nem gravar linhas; `amount` ausente, `0`, negativo e `10.005` → 400 `VALIDATION_ERROR`. Verificar que `mvn -pl corebank-gateway test` passa
- [x] 4.2 No mesmo teste (ou num teste separado), cobrir "falha no meio não deixa estado parcial": `@MockitoSpyBean` no `TransactionRepository` fazendo `saveAll` lançar exceção, e verificar que os dois saldos continuam com os valores de antes (design.md, Risks)
- [x] 4.3 Adicionar um teste de integração simples do lock com duas threads: duas transferências de 70.00 de uma conta com 100.00, disparadas juntas com `CountDownLatch`, resultam em exatamente um sucesso, um `INSUFFICIENT_BALANCE` e saldo final 30.00; e A→B com B→A simultâneas terminam as duas, sem deadlock (dentro de um timeout). A bateria completa com N requisições e a prova "falha antes do fix" ficam com o card 18

## 5. Integração final

- [x] 5.1 Rodar `mvn spotless:apply` e `mvn clean verify` na raiz e verificar build verde em todos os módulos (domain, gateway, scheduler)

## Workflow follow-up

- Abrir PR de `feature/internal-transfer-endpoint` contra `develop`, citando os cards 15 e 16 e documentando na descrição a escolha de lock pessimista (o card 18 pede que essa decisão fique registrada no PR).
- Pedir review de outra pessoa antes do merge (regra 7: nenhum PR se auto-aprova).
- Mover os cards `[TRANSFER] Internal transfer endpoint` e `[TRANSFER] Insufficient balance validation` juntos pelo board.
- Depois do merge, avisar o time que `[TRANSFER] Transaction history endpoint` e `[TRANSFER] Concurrency test` estão desbloqueados.
- Rodar `/opsx:archive` depois que o PR for revisado e mergeado.
