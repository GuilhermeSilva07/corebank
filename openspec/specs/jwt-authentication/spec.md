# JWT Authentication Specification

## Purpose

Validates the JWT access token presented on an incoming HTTP request and establishes the authenticated principal for it, rejecting the request uniformly when no valid token is present.

## Requirements

### Requirement: Access token validation
Whenever a request carries an `Authorization: Bearer <token>` header, the system SHALL validate the token's signature and expiration before the request reaches application code, regardless of whether the target route requires authentication.

#### Scenario: Valid token accepted
- **WHEN** a request carries a Bearer token with a valid signature that has not expired
- **THEN** validation succeeds and the request is treated as authenticated

#### Scenario: Expired token rejected
- **WHEN** a request carries a Bearer token whose signature is valid but whose expiration has passed
- **THEN** validation fails

#### Scenario: Tampered signature rejected
- **WHEN** a request carries a Bearer token whose payload or signature has been altered
- **THEN** validation fails

#### Scenario: Malformed token rejected
- **WHEN** a request carries a Bearer value that is not a well-formed JWT
- **THEN** validation fails

### Requirement: Authenticated principal reflects token subject
Once a token is validated, the authenticated principal's name SHALL equal the user id carried in the token's subject claim.

#### Scenario: Downstream handler reads the correct principal
- **WHEN** a request with a valid token reaches a protected endpoint
- **THEN** the endpoint observes an authenticated principal whose name is the UUID from the token's subject claim

### Requirement: Uniform rejection for missing or invalid credentials on protected routes
Any route that is not explicitly public SHALL reject a request lacking a present and valid access token with HTTP 401 and the project's standard error body (`message`, `code`, `fieldErrors`), without indicating whether the cause was a missing, expired, or tampered token.

#### Scenario: Missing token on a protected route
- **WHEN** a request to a protected route carries no `Authorization` header
- **THEN** the response is HTTP 401 with the standard error body

#### Scenario: Invalid token on a protected route
- **WHEN** a request to a protected route carries a token that fails validation
- **THEN** the response is HTTP 401 with the standard error body identical in shape to the missing-token case

### Requirement: Invalid token rejected even on public routes
If a request carries an `Authorization` header that fails validation, the system SHALL reject it with HTTP 401 even when the target route does not require authentication.

#### Scenario: Malformed token sent to a public route
- **WHEN** a request to a public route (for example `POST /auth/login`) carries a Bearer value that fails validation
- **THEN** the response is HTTP 401 with the standard error body

### Requirement: Public routes remain accessible without credentials
`POST /users`, `POST /auth/login`, and `POST /auth/refresh` SHALL remain accessible without any `Authorization` header present.

#### Scenario: Public route succeeds without a token
- **WHEN** a request to one of the public routes carries no `Authorization` header
- **THEN** the request is processed normally, without a 401 response
