# Core Bank — Arquitetura do Sistema

> Versão em Markdown do doc de arquitetura original (artifact privado). Esta é a fonte de verdade versionada — se um dia divergir do artifact, este arquivo vence, por estar no histórico do Git e acessível a todo o time sem depender de permissão de link.

Uma plataforma de banco digital construída como projeto pessoal de estudo: gestão de contas, transferências internas, PIX simulado, investimentos e empréstimos, estruturada da mesma forma que um sistema bancário real é dividido em módulos, camadas e processos agendados.

- **Pacote base:** `com.corebank`
- **Stack:** Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 · Flyway · JWT

## 1. Arquitetura de módulos

O Core Bank é entregue como três módulos Maven sob um build pai (`corebank-parent`).

| Módulo | Responsabilidade | Depende de |
|---|---|---|
| `corebank-gateway` | Único módulo exposto ao mundo externo. Controllers por feature, validação de requisição, autenticação JWT, exception handling. | `corebank-domain` |
| `corebank-domain` | O banco em si. Entidades JPA, repositórios, services transacionais com toda a regra de negócio (saldo, juros, aprovação de empréstimo). | Nada dos outros dois módulos |
| `corebank-scheduler` | Tudo que não é disparado por requisição de usuário: acúmulo de juros, detecção de parcela vencida. Chama os services de domínio diretamente, no mesmo processo. | `corebank-domain` |

```mermaid
graph LR
  Client["Cliente mobile / web"] -->|HTTPS + JWT| GW["corebank-gateway<br/>controllers · segurança · DTOs"]
  GW -->|chama| DOM["corebank-domain<br/>entidades · services · regras"]
  SCH["corebank-scheduler<br/>juros · vencidos · expiração"] -->|chama| DOM
  DOM --> DB[("PostgreSQL")]
  SCH -.->|roda por timer, sem cliente| CRON(("gatilho cron"))
```

**Regra estrutural:** `corebank-domain` nunca importa nada de `corebank-gateway` ou `corebank-scheduler`. Essa é a única regra que mantém a lógica de negócio testável sem precisar subir HTTP ou um scheduler.

## 2. Camadas dentro do corebank-domain

```mermaid
flowchart TD
  A["Controller (corebank-gateway)"] --> B["Service — lógica de negócio @Transactional"]
  B --> C["Repository — Spring Data JPA"]
  C --> D[("PostgreSQL")]
  B --> E["Entidade de domínio — Account · Transaction · PixKey ..."]
  B -. "lança" .-> F["Exceção de domínio — InsufficientBalanceException etc."]
  F --> G["@RestControllerAdvice — mapeia para contrato de erro HTTP"]
  G --> A
```

**Regra de posicionamento de service:** um `@Service` mora em `corebank-domain` sempre, mesmo que hoje só o gateway o chame. A pergunta "onde eu coloco essa lógica?" nunca depende de quem chama hoje — só existe uma resposta.

Fluxo padrão (exemplo real, cadastro de usuário):

```
Cliente HTTP → RegisterUserRequest (gateway, com @Valid)
                    ↓ controller converte
            RegisterUserCommand (domain, dados puros, sem anotação de validação)
                    ↓ passado pro service
      UserRegistrationService.register(command)  (domain, roda a lógica)
```

O `corebank-gateway` só define DTOs de requisição/resposta e controllers finos que convertem esses DTOs em um `Command` próprio do domínio antes de chamar o service. O domínio nunca enxerga o formato HTTP.

## 3. Fluxo de transferência PIX

O fluxo que mais exige cuidado: precisa ser idempotente (retry do cliente nunca move dinheiro duas vezes) e atômico (débito e crédito acontecem juntos ou nenhum dos dois acontece).

```mermaid
sequenceDiagram
  participant C as Cliente
  participant GW as Gateway
  participant SV as PixTransferService
  participant DB as PostgreSQL

  C->>GW: POST /pix/transfers (Idempotency-Key: k1)
  GW->>SV: transfer(request, idempotencyKey)
  SV->>DB: SELECT transaction WHERE idempotency_key = k1
  alt chave já processada
    DB-->>SV: transação existente
    SV-->>GW: 200 OK (resultado anterior, sem nova movimentação)
  else nova transferência
    SV->>DB: resolve chave PIX -> conta de destino
    SV->>DB: BEGIN TRANSACTION
    SV->>DB: debita conta de origem
    SV->>DB: credita conta de destino
    SV->>DB: insere transação (status = COMPLETED, key = k1)
    SV->>DB: COMMIT
    SV-->>GW: 201 Created
  end
  GW-->>C: resposta
```

## 4. Fluxo de rendimento de investimento (agendado)

Diferente das transferências, não tem cliente esperando — o `corebank-scheduler` acorda uma vez por dia e atualiza cada posição em aberto.

```mermaid
sequenceDiagram
  participant CRON as Scheduler (diário 00:05)
  participant JOB as YieldAccrualJob
  participant SV as InvestmentService
  participant DB as PostgreSQL

  CRON->>JOB: dispara
  JOB->>DB: SELECT positions WHERE status = ACTIVE
  loop cada posição
    JOB->>SV: accrueDailyYield(position)
    SV->>SV: aplica taxa do produto (juros compostos)
    SV->>DB: UPDATE position.balance
    SV->>DB: INSERT yield_history entry
  end
  JOB-->>CRON: concluído, log do resumo
```

## 5. Modelo de dados principal

Relacionamentos simplificados cobrindo as fases até Empréstimos. Toda tabela que movimenta dinheiro carrega um `idempotency_key` ou é escrita dentro de uma única transação — nunca as duas coisas soltas.

```mermaid
erDiagram
  USER ||--o{ ACCOUNT : possui
  ACCOUNT ||--o{ TRANSACTION : "debita / credita"
  ACCOUNT ||--o{ PIX_KEY : registra
  ACCOUNT ||--o{ INVESTMENT_POSITION : mantém
  ACCOUNT ||--o{ LOAN_PROPOSAL : solicita
  INVESTMENT_PRODUCT ||--o{ INVESTMENT_POSITION : "investido via"
  LOAN_PROPOSAL ||--o{ LOAN_INSTALLMENT : gera

  USER {
    uuid id
    string full_name
    string cpf
    string email
    string password_hash
  }
  ACCOUNT {
    uuid id
    uuid user_id
    string account_number
    decimal balance
  }
  TRANSACTION {
    uuid id
    uuid account_id
    string type
    decimal amount
    string idempotency_key
    timestamp created_at
  }
  PIX_KEY {
    uuid id
    uuid account_id
    string key_type
    string key_value
  }
  INVESTMENT_PRODUCT {
    uuid id
    string name
    decimal annual_rate
  }
  INVESTMENT_POSITION {
    uuid id
    uuid account_id
    uuid product_id
    decimal principal
    decimal balance
    string status
  }
  LOAN_PROPOSAL {
    uuid id
    uuid account_id
    decimal principal
    decimal rate
    int term_months
    string status
  }
  LOAN_INSTALLMENT {
    uuid id
    uuid loan_id
    int number
    date due_date
    decimal amount
    string status
  }
```

## 6. Fases de entrega

Cada fase entrega um pedaço funcional do banco, não um esqueleto vazio — mesma divisão rastreada card a card no Trello.

| Fase | Nome | Entregas principais |
|---|---|---|
| 0 | Fundação | Build multi-módulo, PostgreSQL + Flyway, Docker Compose, CI, Spotless |
| 1 | Auth & Contas | Modelo User + Account, BCrypt, login JWT, refresh token, saldo & extrato |
| 2 | Transferências Internas | Modelo Transaction, transferência atômica, validação de saldo, testes de concorrência |
| 3 | PIX (simulado) | CRUD de chave PIX, transferência instantânea, idempotência, histórico |
| 4 | Investimentos | Modelo produto + posição, aplicar/resgatar, job de rendimento diário, testes |
| 5 | Empréstimos | Simulação, aprovação, cronograma de parcelas, job de vencidos |
| 6 | Produção / Hardening | Contrato de erro, docs OpenAPI, logging estruturado, rate limiting, Docker, checklist OWASP |

Ordem detalhada card a card, com dependências e como dividir entre pessoas: ver o doc de Fluxo de Desenvolvimento (linkado no `CLAUDE.md`) e o board Trello "Core Bank".

## 7. Referência de stack técnica

| Área | Escolha | Por quê |
|---|---|---|
| Linguagem | Java 21 | LTS, records e pattern matching simplificam o código de domínio |
| Framework | Spring Boot 4.1.1 | Padrão de mercado para essa stack |
| Persistência | Spring Data JPA + PostgreSQL | Integridade relacional importa para dinheiro; PostgreSQL é gratuito e pronto para produção |
| Migrations | Flyway | Mudanças de schema versionadas, revisáveis em PRs |
| Auth | Spring Security + JWT | Autenticação stateless, padrão para APIs mobile-first |
| Build | Maven multi-módulo | Impõe os limites entre módulos em tempo de compilação |
| Docs | springdoc-openapi (planejado, fase 6) | Swagger UI gerado a partir do código, nunca fica desatualizado |
| Ambiente local | Docker Compose | Um comando para subir Postgres |
| CI | GitHub Actions | Gratuito para repositórios públicos, alinhado com o fluxo Code Review → QA do Trello |

---

Core Bank é um projeto pessoal de estudo inspirado em bancos digitais como o Nubank. Todos os dados, transferências e cálculos de juros são simulados — nenhum dinheiro real é movimentado.
