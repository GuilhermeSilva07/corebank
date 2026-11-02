# Tasks

## 1. Domain: token validation

- [x] 1.1 Add `InvalidAccessTokenException` in `corebank-domain/.../exception/`, following the existing `InvalidRefreshTokenException` shape, and verify it compiles and is a plain `RuntimeException`
- [x] 1.2 Add `JwtService.parseUserId(String token): UUID`, validating signature and expiration via `Jwts.parserBuilder()`/`parseClaimsJws`, returning the subject as a `UUID`, and wrapping any `JwtException`/`IllegalArgumentException` into `InvalidAccessTokenException`
- [x] 1.3 Extend `JwtServiceTest` with cases for a valid token (returns the original UUID), an expired token (throws `InvalidAccessTokenException`), and a tampered/invalid-signature token (throws `InvalidAccessTokenException`), and verify `mvn -pl corebank-domain test` passes

## 2. Gateway: uniform 401 response

- [x] 2.1 Add `RestAuthenticationEntryPoint` (`corebank-gateway/.../config` or `.../security`) implementing `AuthenticationEntryPoint`, writing the project's standard `ErrorResponse` (`message`, `code`, `fieldErrors: null`) with HTTP 401, and verify it compiles against the existing `ErrorResponse` DTO used by `GlobalExceptionHandler`

## 3. Gateway: request-time token validation

- [x] 3.1 Add `JwtAuthenticationFilter` (`OncePerRequestFilter`) that reads `Authorization: Bearer <token>`: no header -> continue the chain unauthenticated; valid token -> set a `UsernamePasswordAuthenticationToken` with principal name equal to the parsed UUID into `SecurityContextHolder`, then continue; invalid token (via `JwtService.parseUserId` throwing `InvalidAccessTokenException`) -> call `restAuthenticationEntryPoint.commence(...)` and stop the chain, and verify it compiles and injects `JwtService` and `RestAuthenticationEntryPoint`
- [x] 3.2 Wire `SecurityConfig`: register `restAuthenticationEntryPoint` via `.exceptionHandling(e -> e.authenticationEntryPoint(...))` and `jwtAuthenticationFilter` via `.addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`, and verify the app context still starts (`mvn -pl corebank-gateway test` with the existing `CorebankGatewayApplicationTests`)

## 4. Integration verification

- [x] 4.1 Add a `@SpringBootTest` + `MockMvc` test (new file, e.g. `JwtAuthenticationFilterIntegrationTest`, following the pattern in `UserRegistrationIntegrationTest`/`LoginIntegrationTest`) covering: a request to `GET /accounts/{id}/statement` with a valid token reaches the controller with the principal name equal to the token's subject (expect it to get past authentication - a 200 or whatever the endpoint already returns for a real owner); the same route with no token and with a tampered token both return HTTP 401 with the standard error body in the same shape; `POST /auth/login` still succeeds without any `Authorization` header; and verify `mvn -pl corebank-gateway test` passes

## Workflow follow-up

- Run `mvn spotless:apply` then `mvn clean verify` at the repo root before opening the PR.
- Align with Lucas before starting, since `[AUTH] Get account balance endpoint` depends on the same filter - confirm he hasn't started equivalent work.
- Open the PR against `develop`; it unblocks QA on `[AUTH] Account statement endpoint` and the "Token" block of card #13 (`[AUTH] Integration tests for auth flow`).
- Archive this change after the PR is reviewed and merged (no PR self-approves, per team convention).
