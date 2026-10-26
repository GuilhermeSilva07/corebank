# Design

## Context

See proposal.md - Why. Relevant current state:

- `SecurityConfig` (`corebank-gateway`) already marks `POST /users`, `POST /auth/login`, `POST /auth/refresh` as `permitAll()` and everything else as `anyRequest().authenticated()`, with `SessionCreationPolicy.STATELESS`. No filter populates `Authentication`, so every other request is anonymous and gets rejected before reaching a controller.
- `JwtService` (`corebank-domain`) only has `generateAccessToken(UUID)`. It has no Spring Security dependency today - it only depends on `io.jsonwebtoken` (JJWT 0.11.5).
- `GlobalExceptionHandler` (`@RestControllerAdvice`) only sees exceptions thrown from controller methods. A servlet filter runs before `DispatcherServlet`, so a rejection decided inside the filter chain never reaches it - the standard `ErrorResponse` body has to be written by something inside the filter chain itself.
- `AccountController.getStatement` already assumes `UUID.fromString(principal.getName())` - the principal's name must be the user's UUID as a string.

## Goals / Non-Goals

**Goals:**
- Populate `Authentication` for any request carrying a valid access token, with the principal name set to the token's subject (user UUID).
- Reject requests lacking a valid, present token on non-public routes with HTTP 401 and the project's standard error body, through exactly one code path.
- Keep JWT parsing/validation logic in `corebank-domain`, per the module boundary rule - the gateway only orchestrates HTTP/Security concerns.

**Non-Goals:**
- Authorization rules beyond "is there a valid principal" (e.g. resource ownership checks) - those belong to the endpoints themselves (see `AccountController`'s existing ownership check) and to card #13's "Protected endpoint" block.
- Refresh token handling - already implemented by `RefreshTokenService`, untouched here.
- Distinguishing *why* a token was rejected in the response body - the spec explicitly requires a uniform response regardless of cause.

## Decisions

### 1. A single `AuthenticationEntryPoint` writes every 401, not the filter itself
Two options were considered:
- **(A) Filter writes the response directly**: simpler to unit-test in isolation, but duplicates `ErrorResponse` JSON serialization outside `GlobalExceptionHandler`, and still needs a second code path for "no token at all" (that case never reaches the filter's catch block - it's Spring Security's own authorization check that rejects it).
- **(B) Custom `AuthenticationEntryPoint` (chosen)**: `JwtAuthenticationFilter` only decides whether a *present* token is valid. When it is invalid, the filter calls the entry point directly (`restAuthenticationEntryPoint.commence(request, response, ex)`) instead of writing the response itself. When no token is present at all, Spring Security's own `ExceptionTranslationFilter` already routes anonymous-and-rejected requests to the configured entry point (`authenticationTrustResolver.isAnonymous(...)` check), with no extra wiring needed. Result: exactly one component (`RestAuthenticationEntryPoint`) ever writes the 401 body, for both causes.

Registered via:
```
http.exceptionHandling(e -> e.authenticationEntryPoint(restAuthenticationEntryPoint))
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
```

### 2. The filter validates whenever the header is present, regardless of route
Confirmed with the team: `JwtAuthenticationFilter` does not consult the list of public routes. If `Authorization` is absent, it passes through unauthenticated and lets the existing `authorizeHttpRequests` rules decide (public routes pass, protected routes reject). If `Authorization` is present, it is always validated - including on public routes - so a malformed/expired/tampered Bearer value on `POST /auth/login` still gets rejected with 401. This was chosen over "ignore the header on public routes" because it keeps the filter's branching to one axis (header present or not) instead of two (header present AND route public/protected), and fails closed rather than silently accepting a broken token.

### 3. JWT parsing/validation lives in `JwtService` (domain), not in the filter
`JwtService` gains `parseUserId(String token): UUID`. It validates signature and expiration via `Jwts.parserBuilder()...parseClaimsJws(token)` and returns the subject as a `UUID`. Any `JwtException` (expired, malformed, bad signature) or `IllegalArgumentException` (subject not a valid UUID) is caught and re-thrown as a new `InvalidAccessTokenException` (`corebank-domain.exception`, same shape as the existing `InvalidRefreshTokenException`) - a single exception type, so the gateway side never has to re-derive "why" to keep the response uniform. This keeps `corebank-domain` free of any Spring Security import, consistent with the existing module boundary.

### 4. `JwtAuthenticationFilter` and `RestAuthenticationEntryPoint` live in `corebank-gateway`
Both are HTTP/Security concerns (servlet filter, `HttpServletResponse` writing) and belong next to `SecurityConfig`, `GlobalExceptionHandler`, and the existing `AuthController`/`AccountController`. `RestAuthenticationEntryPoint` reuses the existing `ErrorResponse` DTO from `gateway.dto.error` to stay consistent with `GlobalExceptionHandler`'s format.

Implementation note discovered while building this: Spring Boot 4.1.1 ships Jackson 3 (`tools.jackson.core:jackson-databind`) as its default JSON library; `com.fasterxml.jackson.databind.ObjectMapper` is only on the classpath transitively, at `runtime` scope, via `jjwt-jackson`, and isn't resolvable at compile time. The injectable, auto-configured bean is `tools.jackson.databind.ObjectMapper` - use that import in `RestAuthenticationEntryPoint`, not the legacy `com.fasterxml` one.

### 5. Test scope for this change: JwtService unit tests + one wiring-level integration test
- Unit tests on `JwtService.parseUserId` (valid, expired, tampered signature) - this is the card's explicit acceptance criterion and needs no Spring context, following the existing `JwtServiceTest` style.
- One `@SpringBootTest` + `MockMvc` test proving the `SecurityConfig` wiring itself: valid token against a protected route reaches the controller with the right principal (200), missing/tampered token against a protected route returns the standard 401 body, and a public route still succeeds without a token. This is the only way to catch a wiring mistake (e.g. filter registered in the wrong position, entry point not wired) that an isolated filter unit test would miss.
- A dedicated unit test of the filter in isolation (mocked `HttpServletRequest`/`FilterChain`) was considered and dropped as redundant: the JwtService unit tests already cover the validation branches, and the wiring-level integration test already covers the filter's behavior inside the real chain.
- Resource-ownership scenarios ("correct owner" vs "wrong owner" on `/accounts/{id}/statement`) are explicitly out of scope here - they belong to card #13's "Protected endpoint" block, which this change unblocks but does not implement.

## Risks / Trade-offs

- **[Risk]** Validating the token on every request with a header, even on public routes, means a client that reuses an old/expired access token when calling `/auth/refresh` (a reasonable client bug) now gets a 401 instead of being allowed through. → **Mitigation**: this was a deliberate, confirmed trade-off (see Decision 2) in favor of simpler, fail-closed logic; the public endpoints don't require a token, so a well-behaved client simply never sends one there.
- **[Risk]** `InvalidAccessTokenException` collapses "expired" and "tampered" into one exception type, which could make a future requirement to log/metric these separately slightly more awkward. → **Mitigation**: the original `JwtException` subtype is still available at the throw site if finer-grained logging is ever needed internally; only the externally-visible response is collapsed.

## Migration Plan

No data migration. Deploys as a normal gateway change: once merged, any client relying on hitting a protected endpoint without a token (there shouldn't be one, since those endpoints already reject anonymous requests with 403 today) would see 401 instead of 403 - a stricter but compatible change for well-behaved clients. No feature flag needed; rollback is a normal revert.
