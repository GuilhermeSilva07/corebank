# Core Bank

Banco digital simulado (estilo Nubank), construído como projeto de estudo em Java/Spring Boot. Nenhum dinheiro real é movimentado — todos os dados e transações são simulados.

## Stack

Java 21 · Spring Boot 4.1.1 · Spring Security 7.x · Spring Data JPA · PostgreSQL 16 · Flyway · Maven multi-módulo · Lombok · JJWT.

## Estrutura dos módulos

```
corebank-parent/
├── corebank-domain/     # Entidades, repositórios, services, regras de negócio
├── corebank-gateway/    # REST controllers, DTOs, segurança, exception handler
└── corebank-scheduler/  # Jobs agendados (rendimento, parcelas vencidas)
```

Detalhes de arquitetura, fluxos e modelo de dados: [ARCHITECTURE.md](ARCHITECTURE.md).

## Como rodar localmente

### Pré-requisitos

- Java 21 (JDK)
- Maven
- Docker (para o PostgreSQL)

### Passo a passo

1. Clone o repositório e entre na pasta:
   ```bash
   git clone https://github.com/GuilhermeSilva07/corebank.git
   cd corebank
   ```

2. Copie o arquivo de exemplo de variáveis de ambiente e ajuste os valores:
   ```bash
   cp .env.example .env
   ```
   Preencha `POSTGRES_PASSWORD` e `JWT_SECRET` com valores próprios (não use `change_me` fora do ambiente local).

3. Suba o PostgreSQL:
   ```bash
   docker compose up -d
   ```

4. Formate o código e valide o build (rode isso sempre antes de commitar):
   ```bash
   mvn spotless:apply
   mvn clean verify
   ```

5. Rode a aplicação (módulo `corebank-gateway`):
   ```bash
   cd corebank-gateway
   mvn spring-boot:run
   ```

A API sobe em `http://localhost:8080`. Exemplo de teste rápido (cadastro de usuário):

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Teste",
    "cpf": "12345678901",
    "email": "teste@corebank.com",
    "password": "SenhaForte123",
    "birthDate": "1995-05-20",
    "address": {
      "street": "Rua Teste",
      "number": "100",
      "neighborhood": "Centro",
      "city": "Sao Paulo",
      "state": "SP",
      "zipCode": "01000000"
    }
  }'
```

## Fluxo de contribuição

- Branch a partir de `develop`: `feature/<descrição-curta>`.
- Commits em [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`).
- Todo PR precisa de revisão antes de mergear — nenhum PR se auto-aprova, mesmo do dono do repositório.
- Card só é considerado concluído depois de passar por Code Review e QA/Testing (ver o board Trello "Core Bank").

Convenções completas: [CLAUDE.md](CLAUDE.md) (também serve como contexto para agentes de IA que ajudarem no desenvolvimento).

## Status

Projeto em desenvolvimento ativo. Roadmap completo por fases (SETUP → AUTH → TRANSFER → PIX → INVEST → LOAN → PLATFORM) documentado no [ARCHITECTURE.md](ARCHITECTURE.md) e rastreado no Trello.
