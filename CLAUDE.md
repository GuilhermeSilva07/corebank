# Core Bank — contexto para agentes de IA

Projeto de estudo: um banco digital simulado (estilo Nubank), construído em Java/Spring Boot por 3 pessoas (Guilherme, Bob Petrillo, Lucas Kevin) como exercício de arquitetura, Git Flow e processo de time real. Nenhum dinheiro real é movimentado — todos os dados e transações são simulados.

## Antes de sugerir ou revisar qualquer coisa

Leia estes documentos primeiro — eles são a fonte da verdade, este arquivo só resume e aponta pra eles. Os dois primeiros são artifacts privados: se o link não abrir, peça pro Guilherme compartilhar (ele é o dono).

- **Arquitetura completa** (módulos, camadas, fluxos de PIX/investimento, modelo de dados, roadmap): https://claude.ai/code/artifact/58005508-38a5-4ec1-b732-abdbe815184d
- **Fluxo de desenvolvimento** (portões do Kanban, ordem dos cards por fase, como dividir entre pessoas): https://claude.ai/code/artifact/b07a2971-02a9-4acb-a384-825499e6ab16
- **Convenções de time** (regra de revisão, onde a lógica de negócio mora): https://trello.com/c/Be7iJdq1/48-team-conventions-branching-commits-code-review
- Estado atual do board: Trello "Core Bank" (46+ cards, fases SETUP → AUTH → TRANSFER → PIX → INVEST → LOAN → PLATFORM)

⚠️ O doc de arquitetura ainda diz pacote `com.guilhermesilva.corebank` e "Spring Boot 3" — **desatualizado**. O código real usa `com.corebank.*` e Spring Boot 4.1.1 (confira `pom.xml` e os `package` das classes antes de confiar no texto do doc nesses dois pontos específicos).

## Regras não-negociáveis (violação = bloqueante em qualquer review)

1. **Módulo**: toda classe `@Service` mora em `corebank-domain`, nunca em `corebank-gateway` ou `corebank-scheduler` — independente de quem a chama hoje. `corebank-domain` nunca importa nada de `corebank-gateway` ou `corebank-scheduler`, em nenhuma direção.
2. **DTO vs Command**: controllers em `corebank-gateway` são finos — convertem o DTO de requisição (`@Valid`) num objeto `Command` (record, dados puros, sem anotação de validação) do domain antes de chamar o service. O domain nunca aceita um DTO do gateway diretamente. Exemplo real: `RegisterUserRequest` (gateway) → controller mapeia → `RegisterUserCommand` (domain) → `UserRegistrationService.register(command)`.
3. **Segredos**: nunca hardcoded em `application.properties` nem em qualquer arquivo versionado. Sempre `${VAR:fallback-de-dev-ou-CI}`, com o valor real só no `.env` (gitignored) e um placeholder no `.env.example`. Mesmo padrão pra `POSTGRES_PASSWORD` e `JWT_SECRET`.
4. **Lombok em DTOs**: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` explícitos — não usar `@Data`, que gera `toString()` incluindo todos os campos e pode vazar senha em log se o objeto for logado sem querer.
5. **Erros**: toda exceção de domínio passa pelo `@RestControllerAdvice` (`GlobalExceptionHandler`), nunca try/catch ad-hoc em controller. Resposta de erro sempre no formato `{message, code, fieldErrors}`.
6. **Teste unitário é responsabilidade de quem escreveu o código** (decisão do time, 2026-09-19) — não delegar pra outra pessoa, agiliza o fluxo. Já os cards de teste de integração que fecham uma fase inteira (ex: "[AUTH] Unit + integration tests for auth flow") continuam sendo do time como um todo, não de um autor específico.
7. **Nenhum PR se auto-aprova** — mesmo o dono do repositório precisa de revisão antes de mergear. Card só vai pra "Done" depois de passar por Code Review e QA/Testing.
8. **Nenhuma fase abre antes da anterior fechar 100%** — não sugerir trabalho em TRANSFER/PIX/INVEST/LOAN enquanto AUTH não fechar, mesmo que uma dependência técnica pontual já esteja satisfeita. Dentro da mesma fase, sim, dá pra paralelizar (card de "Design domain model" é solo primeiro, depois os endpoints entre 2+ pessoas).
9. **Evitar user enumeration em endpoints de auth**: erro de credencial inválida (email inexistente vs senha errada) sempre retorna a mesma mensagem genérica e o mesmo status.

## Stack (verificado no `pom.xml`, não no doc de arquitetura)

Java 21 · Spring Boot 4.1.1 · Spring Security 7.x · Spring Data JPA · PostgreSQL 16 · Flyway · Maven multi-módulo · Lombok · JJWT (`io.jsonwebtoken`, v0.11.5) · Spotless (Google Java Format).

## Estrutura dos módulos

- **`corebank-domain`** — entidades JPA, repositórios, services transacionais, commands. Toda regra de negócio vive aqui, sem depender de HTTP.
- **`corebank-gateway`** — REST controllers, DTOs, config de segurança (`SecurityFilterChain`), exception handler. Único módulo exposto externamente; sem lógica de negócio.
- **`corebank-scheduler`** — jobs `@Scheduled` (cálculo de rendimento diário, detecção de parcela vencida). Chama services do domain diretamente. **Nunca roda migration Flyway** — só o `corebank-gateway` é dono do schema.

## Git / PR

- Branch: `feature/<descrição-curta>` (ex: `feature/auth-jwt-login`), a partir de `develop`.
- Commit: Conventional Commits (`feat:`, `fix:`, `chore:`), em inglês, no imperativo.
- Antes de todo commit: `mvn spotless:apply` seguido de `mvn clean verify`.
- PR sempre contra `develop`, nunca direto em `main`.

## Como um agente deve se comportar aqui

- **Ao revisar um PR**: comparar contra as 9 regras acima primeiro (a maioria delas veio de violações reais pegas em review neste projeto), depois contra os critérios de aceitação específicos do card do Trello. Rodar o build localmente (`mvn clean verify`) sempre que possível, não confiar só na leitura do diff — já aconteceu de o diff parecer correto e o CI quebrar por falta de env var.
- **Ao sugerir próximos passos**: respeitar a ordem de fases do doc de Fluxo de Desenvolvimento — nunca sugerir pular pra uma fase mais avançada só porque a dependência técnica pontual existe.
- **Ao ver um placeholder deliberado** (ex: refresh token com valor fixo antes do card dedicado existir): não tratar como bug se já existe um card no roadmap cobrindo aquilo — checar o board antes de reportar.
- **Ao propor divisão de trabalho entre pessoas**: domain model é sempre solo; endpoints da mesma fase podem paralelizar depois que o modelo mergeia; testes unitários ficam com quem escreveu o código sendo testado.
