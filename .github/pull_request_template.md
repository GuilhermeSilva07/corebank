## Card do Trello

<!-- Link do card correspondente -->

## O que este PR faz

<!-- Resumo curto, 1-3 linhas -->

## Checklist antes de pedir review

- [ ] `mvn spotless:apply` rodado (sem diff de formatação pendente)
- [ ] `mvn clean verify` passa localmente
- [ ] Nenhum segredo/senha/token hardcoded (checar `application.properties` e qualquer arquivo versionado — segredo real vai só no `.env`, nunca commitado)
- [ ] `@Service` novo está em `corebank-domain`, não em `corebank-gateway`/`corebank-scheduler`
- [ ] DTOs usam `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` (não `@Data`, se o DTO tiver campo sensível como senha)
- [ ] Testado manualmente (Postman/curl) — colar evidência abaixo se o card pedir
- [ ] Teste(s) unitário(s) cobrindo o critério de aceitação do card (escrito por quem desenvolveu, per convenção do time)
- [ ] Critérios de aceitação do card do Trello cumpridos (não só "compilou")

## Evidência de teste manual (se aplicável)

<!-- Cole aqui request/response do Postman ou curl -->

## Observações para quem revisar

<!-- Algo que o revisor deveria saber: decisão de design, trade-off, dependência pendente -->
