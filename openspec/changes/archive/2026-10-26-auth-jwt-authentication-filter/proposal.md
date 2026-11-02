# Proposal

## Why

Login e refresh já emitem o access token JWT, mas nada na aplicação o lê de volta: não existe filtro de autenticação no `SecurityFilterChain`. Toda chamada a uma rota protegida (ex: `GET /accounts/{id}/statement`) cai como anônima e recebe 403 puro, mesmo com um token válido. Isso bloqueia a validação ponta a ponta do endpoint de statement (parado em QA/Testing) e o bloco "Token" do card de testes de integração de auth.

## What Changes

- Adiciona `JwtAuthenticationFilter` (`corebank-gateway`), registrado antes do `UsernamePasswordAuthenticationFilter`, que tenta validar o header `Authorization: Bearer <token>` sempre que ele existir — em qualquer rota, pública ou protegida.
- `JwtService` (`corebank-domain`) ganha `parseUserId(String token): UUID`, validando assinatura e expiração do token; lança a nova `InvalidAccessTokenException` para token expirado, assinatura inválida ou malformado, sem distinguir o motivo externamente.
- Adiciona `RestAuthenticationEntryPoint` (`corebank-gateway`), único ponto que escreve o `ErrorResponse{message, code, fieldErrors: null}` padrão do projeto com status 401 — tanto para ausência de token (via fluxo padrão do Spring Security para autenticação anônima) quanto para token inválido (chamado diretamente pelo filtro).
- `SecurityConfig` passa a registrar o filtro (`addFilterBefore`) e o entry point (`exceptionHandling`).
- Token válido: o `Authentication` resultante tem como nome do principal o UUID do usuário (subject do token), compatível com o contrato que o `AccountController` já assume (`UUID.fromString(principal.getName())`).
- Rotas públicas (`POST /users`, `POST /auth/login`, `POST /auth/refresh`) continuam sem exigir token, mesmo com este filtro no chain.

## Capabilities

### New Capabilities
- `jwt-authentication`: valida o access token JWT apresentado em uma requisição HTTP e estabelece o principal autenticado, ou rejeita a requisição com 401 padronizado, para qualquer rota que não seja explicitamente pública.

### Modified Capabilities
(nenhuma — não existem specs anteriores no projeto; este é o primeiro capability registrado.)

## Impact

- `corebank-domain`: `JwtService` ganha um método novo; nova exceção `InvalidAccessTokenException`.
- `corebank-gateway`: novo `JwtAuthenticationFilter`, novo `RestAuthenticationEntryPoint`, `SecurityConfig` modificado para registrar ambos.
- Desbloqueia: QA do card `[AUTH] Account statement endpoint`, o bloco "Token" do card `[AUTH] Integration tests for auth flow` (#13), e o card `[AUTH] Get account balance endpoint` (Lucas), que depende do mesmo mecanismo de autenticação.
- Sem migration de banco nem endpoint novo; mudança restrita à camada de segurança HTTP e ao parsing de JWT no domain.
