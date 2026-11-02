# Design

## Context

Motivação em proposal.md (Why). Requisitos em `specs/transaction-ledger/spec.md`. Estado atual relevante:

- `accounts.balance` é `numeric(15,2)` (V1). As entidades atuais (`Account`, `RefreshToken`) usam `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` na classe, `@GeneratedValue` para o `id` UUID e `@ManyToOne` com o fetch padrão (EAGER).
- `spring.jpa.hibernate.ddl-auto=validate`: o Hibernate confere nomes e tipos de colunas contra o schema do Flyway, mas **não** confere CHECK constraints, UNIQUE compostos nem índices.
- Migrations ficam em `corebank-gateway/src/main/resources/db/migration/` (V1 a V3 existem). O time reservou V4 para `transactions`.
- `StatementItemResult(id, type, amount, timestamp, runningBalance)` já existe como contrato do extrato. O tipo vem como `String` e o comentário prevê "CREDIT"/"DEBIT".
- Os testes de integração ficam em `corebank-gateway` (`@SpringBootTest` + Postgres real, por exemplo `AccountBalanceIntegrationTest`), porque é o único módulo que sobe o contexto com Flyway.

## Goals / Non-Goals

**Goals:**
- Schema e entidade que atendam TRANSFER, PIX, INVEST e LOAN sem outra migration nesta tabela.
- Fazer valer no banco as invariantes da spec que não dependem de código: FK, `amount > 0` e unicidade da idempotency key por conta.

**Non-Goals:**
- Serviço de transferência, locking de `accounts` ou cálculo de `balance_after` (`[TRANSFER] Internal transfer endpoint`).
- Queries de histórico e filtros (`[TRANSFER] Transaction history endpoint`). Só o índice que elas vão usar entra aqui.
- Contabilidade de partidas dobradas completa (plano de contas, contas internas do banco). O "double-entry" aqui quer dizer "os dois lados de uma transferência entre contas são registrados".

## Decisions

### 1. Agrupar as pernas com `transfer_id` na própria linha (sem tabela de cabeçalho)
Alternativas consideradas:
- **(A) `transfer_id uuid` compartilhado (escolhida):** uma tabela só; agrupa N pernas; a contraparte sai por self-join no índice `(transfer_id)`.
- **(B) `counterpart_account_id`:** o extrato mostra "pra quem" sem join, mas não agrupa e não faz sentido em INVEST/LOAN, onde não há conta do outro lado.
- **(C) Tabela `transfers` (cabeçalho) + `transactions` (pernas):** é o desenho contábil mais correto e deixaria idempotência/status/canal numa linha só, mas acrescenta uma entidade e um relacionamento fora do escopo do card.

`transfer_id` é `NOT NULL` e sempre preenchido, inclusive em operações de uma perna. Assim as queries não precisam tratar NULL. Não há FK, porque não existe tabela de cabeçalho. Quem gera o UUID é a aplicação, no service que cria a operação, e não `@GeneratedValue`.

### 2. Idempotency key só no DEBIT, com `UNIQUE (account_id, idempotency_key)`
- Uma UNIQUE global em `idempotency_key` não aceita que as duas pernas compartilhem a chave, e ainda rejeitaria indevidamente chaves iguais geradas por clientes diferentes. O escopo por conta segue o modelo usual (Stripe e APIs bancárias).
- No Postgres, UNIQUE aceita vários NULL, então o "unique quando preenchido" vem sem índice parcial. O CREDIT e as linhas sem chave ficam com NULL.
- A constraint composta já cria o índice que `[PIX] Idempotency key handling` vai usar para buscar a operação original.
- `varchar(255)`, no mesmo padrão das colunas texto da V1.

### 3. Enums como `varchar` + `@Enumerated(EnumType.STRING)`, sem CHECK
- Adicionar um valor ao enum não exige migration, que é o que o card pede. Com uma CHECK listando os valores, cada valor novo viraria uma migration.
- O trade-off é que o banco aceita qualquer string se alguém escrever por fora da aplicação. Isso é aceitável: só a aplicação escreve nesta tabela.
- Nomes: `TransactionType` (DEBIT/CREDIT) mantém o nome do card e casa com `StatementItemResult.type`. `TransactionChannel` foi escolhido em vez de `TransactionOrigin`/`Source` para não ser confundido com "conta de origem".
- `TransactionStatus` já nasce com `PENDING`, `COMPLETED` e `FAILED`. Hoje só `COMPLETED` é escrito. Uma falha síncrona faz rollback e não persiste nada (gravar FAILED exigiria `REQUIRES_NEW`, o que fica fora do escopo).

### 4. `amount > 0` como CHECK no banco
Diferente dos enums, essa invariante nunca vai mudar. A CHECK protege contra sinal invertido mesmo se um service futuro errar. O tipo é `numeric(15,2)`, igual a `accounts.balance`. `balance_after` usa o mesmo tipo e **não** tem CHECK `>= 0`: a regra de saldo negativo é de negócio (pode existir limite ou cheque especial no futuro) e fica no service.

### 5. Entidade imutável exceto `status`
- `@Getter` na classe, **sem** `@Setter` de classe. Isso diverge de `Account`/`RefreshToken`, de propósito: é um ledger.
- A criação passa por construtor ou factory estático (`Transaction.debit(...)`/`credit(...)`, ou um único construtor com todos os campos); a forma exata fica para a implementação.
- A mudança de status passa por métodos de intenção (`markCompleted()`, `markFailed()`) em vez de `setStatus`.
- `@NoArgsConstructor(access = PROTECTED)` para o JPA.
- Colunas imutáveis com `@Column(updatable = false)`, para o Hibernate nem incluí-las em um UPDATE.
- A regra 4 do projeto (sem `@Data`) se refere a DTOs, mas vale o mesmo cuidado aqui: nada de `@ToString` incluindo `account`, para não disparar lazy load em log.

### 6. `@ManyToOne(fetch = LAZY)` para `Account`
O histórico vai listar centenas de linhas da mesma conta. Com EAGER, cada leitura carregaria `Account → User` sem necessidade. O lado `Account` não ganha `@OneToMany` de transações, para não carregar o ledger inteiro junto com a conta.

### 7. `created_at` definido pela aplicação
Segue o padrão de `Account`/`User` (`Instant` setado no código, `timestamptz` sem DEFAULT). O valor é atribuído na construção da entidade.

### 8. Índices
- `(account_id, created_at DESC)`: histórico do mais recente para o mais antigo com filtro de período, que é o padrão de acesso de `[TRANSFER] Transaction history endpoint`.
- `(transfer_id)`: achar a contraparte.
- A UNIQUE `(account_id, idempotency_key)` já gera o próprio índice.

## Risks / Trade-offs

- [`ddl-auto=validate` não verifica CHECK, UNIQUE composto nem índices; um erro na migration passaria despercebido] → Teste de integração no gateway que insere `amount = 0` e chave duplicada na mesma conta e espera a violação, além de chave igual em contas diferentes passando.
- [Sem tabela de cabeçalho, status/canal/idempotência ficam por perna e podem divergir entre as duas linhas de um mesmo `transfer_id`] → A consistência entre pernas é responsabilidade do service que escreve (`[TRANSFER] Internal transfer endpoint`), numa única transação. Se isso doer no futuro, a opção C (cabeçalho) continua possível com uma migration aditiva.
- [Enums sem CHECK aceitam lixo vindo de fora da aplicação] → Só a aplicação escreve; aceito.
- [`PENDING` existe mas `balance_after` não tem semântica clara para uma movimentação ainda não aplicada] → Hoje nada escreve PENDING (o PIX delega para o transfer síncrono). Se o PIX assíncrono surgir, a decisão fica com ele.

## Migration Plan

- V4 é puramente aditiva (`CREATE TABLE` + índices) e não toca em `accounts`. Não há backfill: nenhuma movimentação foi registrada até agora.
- Rollback: como não há migration "down" no projeto, desfazer seria uma nova migration com `DROP TABLE transactions`. Só é seguro antes de qualquer card de TRANSFER escrever dados.
- A versão V4 é reservada. Se outro PR mergear uma V4 antes, renomeie antes do merge, e não depois.
