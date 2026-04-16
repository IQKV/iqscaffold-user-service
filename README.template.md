# Project Name 🔐

<!-- TEMPLATE: This README.template.md is a starter template. Copy parts into your real README.md and replace placeholders. -->

<details>
  <summary><strong>How to use this template (click to expand)</strong></summary>

1. Rename the title above to your service name and optionally add a logo right below it.
2. Add badges (build, license) under the title.
3. Fill each section below with your actual service content.
4. Update the API table to reflect your actual endpoints and auth requirements.
5. Update the bounded context list to match your actual Spring Modulith modules.
6. Update the environment variables table to match your `application.yml` bindings.
7. Remove this guidance block after you finish customizing.

</details>

- Add your service logo.
- Write a short introduction — what the service owns and which platform it belongs to.
- If you are using badges, add them here.

<details>
  <summary><strong>Badge examples (optional)</strong></summary>

- Build: <code>![CI](https://img.shields.io/github/actions/workflow/status/ORG/REPO/build-java-project.yml?label=CI)</code>
- License: <code>![License](https://img.shields.io/github/license/ORG/REPO)</code>
- Java: <code>![Java](https://img.shields.io/badge/java-21-blue)</code>
- Spring Boot: <code>![Spring Boot](https://img.shields.io/badge/spring--boot-3.x-brightgreen)</code>

</details>

## About

Describe the service's responsibilities in plain language:

- What domain does it own (identity, users, organizations, tenants)?
- What does it produce for other services (JWT tokens, JWKS, user events)?
- What cross-cutting concerns does it handle (auth, email verification, rate limiting)?

## Quick Links

- [API Documentation](./docs/api/README.md)
- [Architecture Overview](./docs/architecture/README.md)
- [Deployment Guide](./docs/deployment/README.md)
- [Contributing Guidelines](.github/CONTRIBUTING.md)

## API

Base path: `/api/v1`

### Public Endpoints

| Method | Path                     | Description                                |
| ------ | ------------------------ | ------------------------------------------ |
| `POST` | `/public/signup`         | Self-service tenant provisioning (no auth) |
| `POST` | `/auth/login`            | Authenticate user, receive token pair      |
| `POST` | `/auth/refresh`          | Rotate access + refresh tokens             |
| `POST` | `/auth/validate`         | Validate JWT for gateway introspection     |
| `GET`  | `/auth/email/verify`     | Verify email address via token link        |
| `POST` | `/auth/email/resend`     | Resend verification email (rate limited)   |
| `POST` | `/auth/password/forgot`  | Initiate password reset                    |
| `POST` | `/auth/password/reset`   | Complete password reset                    |
| `GET`  | `/.well-known/jwks.json` | JWK Set for downstream JWT validation      |

### Protected Endpoints (JWT required)

| Method  | Path                    | Auth | Description            |
| ------- | ----------------------- | ---- | ---------------------- |
| `GET`   | `/users/me`             | JWT  | Get own profile        |
| `PATCH` | `/users/me/password`    | JWT  | Change own password    |
| `GET`   | `/users/me/preferences` | JWT  | Get own preferences    |
| `PATCH` | `/users/me/preferences` | JWT  | Update own preferences |
| `POST`  | `/auth/logout`          | JWT  | Revoke current session |
| `POST`  | `/auth/logout-all`      | JWT  | Revoke all sessions    |

### Admin Endpoints (ADMIN / SUPER_ADMIN)

| Method                | Path                                 | Description                        |
| --------------------- | ------------------------------------ | ---------------------------------- |
| `GET/POST/PUT/DELETE` | `/admin/users/**`                    | User CRUD                          |
| `GET/POST/PUT/DELETE` | `/admin/organizations/**`            | Organization CRUD                  |
| `GET/POST/PUT/DELETE` | `/admin/organization-preferences/**` | Organization preference management |
| `GET/POST/PUT/DELETE` | `/admin/tenants/**`                  | Tenant management (SUPER_ADMIN)    |

> Replace with your actual endpoints. Document the authority requirement for each admin route.

## Tech Stack

- Java 21 / Spring Boot 3.x
- Spring Data JPA + Hibernate (schema-per-tenant multi-tenancy) + PostgreSQL
- Liquibase for system and per-tenant schema migrations
- Redis for JWT blacklist, token state, and session management
- RabbitMQ for async user events (user.created, user.verified, etc.)
- JJWT (HS256/RS256) for JWT signing and validation
- Spring OAuth2 Authorization Server for JWKS endpoint
- Ehcache 3 (JCache) as Hibernate L2 cache
- MinIO / S3 for avatar/file storage (Apache Tika for MIME detection)
- Thymeleaf for transactional email templates
- Spring Modulith for module boundary enforcement
- Micrometer + Prometheus + OpenTelemetry tracing

## Prerequisites

- JDK 21 (Eclipse Temurin)
- Maven 3.9+
- Node.js >= 22.x & pnpm (git hooks)
- Docker & Docker Compose

## Quick Start

```bash
# Clone the repository
git clone https://github.com/ORG/REPO.git
cd REPO

# Install git hooks
pnpm install

# Copy environment variables
cp .env.example .env.local
# Edit .env.local with your local values

# Start dependencies (PostgreSQL, Redis, RabbitMQ, MinIO)
docker compose up -d

# Run the service
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# → API:      http://localhost:8080
# → Actuator: http://localhost:8081/actuator/health
# → Swagger:  http://localhost:8080/swagger-ui.html
# → JWKS:     http://localhost:8080/.well-known/jwks.json
```

## JWT Token Structure

Access tokens carry comprehensive user context consumed by downstream services:

```json
{
    "sub": "1",
    "userId": 1,
    "username": "john.doe",
    "email": "john.doe@example.com",
    "authorities": ["USER", "CRM_ACCESS"],
    "permissions": ["READ_PROFILE"],
    "firstName": "John",
    "lastName": "Doe",
    "tenantId": "acme-corp",
    "organizationId": 42,
    "type": "access",
    "jti": "unique-token-id",
    "iss": "iqscaffold-user-service",
    "iat": 1634567890,
    "exp": 1634568790
}
```

> Update claims to match what your service actually puts in the token.

## Authority Structure

Authorities are stored in the PUBLIC schema (system-wide) while user-authority mappings live in tenant schemas:

| Authority        | Category | Description                                |
| ---------------- | -------- | ------------------------------------------ |
| `SUPER_ADMIN`    | admin    | Full platform access across all tenants    |
| `TENANT_OWNER`   | admin    | Full access within own tenant              |
| `ADMIN`          | admin    | User management (no billing by design)     |
| `TENANT_ADMIN`   | admin    | Administrative access within a tenant      |
| `CRM_ACCESS`     | crm      | Basic CRM feature access                   |
| `BILLING_ACCESS` | billing  | Basic billing feature access               |
| `API_ACCESS`     | api      | Platform API access                        |
| `USER`           | core     | Default authority for all registered users |

> Add or remove rows to match your actual authority definitions in `iqscaffold.platform.authorities.definitions`.

## Multi-Tenancy

Strategy: **schema-per-tenant** via Hibernate `MultiTenantConnectionProvider`.

- `TenantContext` holds the current tenant in a thread-local; repositories are tenant-agnostic
- `TenantLiquibaseRunner` applies per-tenant Liquibase migrations on tenant creation
- Cross-tenant operations iterate tenants and execute via `TenantContext.executeInTenantContext()`
- H2 is used for unit tests with automatic schema creation

```java
// Per-tenant operation
long count = TenantContext.executeInTenantContext(tenantId, () -> userRepository.countByEnabledTrue());
```

## Security Highlights

- JWT blacklist backed by Redis with automatic TTL expiration
- Account lockout after configurable failed attempts (default: 5 attempts, 15 min lockout)
- IP-based rate limiting on login and signup endpoints
- Custom `@ValidPassword` and `@ValidUsername` Bean Validation annotations
- Input sanitization (XSS / SQL injection prevention) via `InputSanitizer`
- Security audit logging via `UserAuditLog` entity
- Fail-open strategy for Redis unavailability (availability over strict security)

## Bounded Contexts (Spring Modulith)

```
src/main/java/com/iqscaffold/userservice/
├── authentication/       # Login, logout, token generation, token validation
├── emailverification/    # Email verification tokens, resend, cleanup
├── invitation/           # Organization invitation flows
├── organization/         # Organization CRUD and preferences
├── passwordmanagement/   # Forgot/reset/change password
├── registration/         # Self-service signup and tenant provisioning
├── security/             # Account lockout, rate limiting, audit logging
├── tenancy/              # Schema-per-tenant, TenantContext, Liquibase runner
├── usermanagement/       # User CRUD, preferences, avatar management
├── shared/               # Email service, messaging, feature/authority services
└── infrastructure/       # Spring config, JPA, Redis, RabbitMQ setup
```

> Update module names to match your actual packages. Spring Modulith enforces module boundaries — `ModulithTest` validates them.

## Environment Variables

| Variable                                    | Default                                            | Description                             |
| ------------------------------------------- | -------------------------------------------------- | --------------------------------------- |
| `IQSCAFFOLD_DATABASE_URL`                   | `jdbc:postgresql://localhost:5432/iqscaffold_user` | PostgreSQL JDBC URL                     |
| `IQSCAFFOLD_DATABASE_USERNAME`              | `iqscaffold_user`                                  | Database user                           |
| `IQSCAFFOLD_DATABASE_PASSWORD`              | `iqkv_password`                                    | Database password                       |
| `IQSCAFFOLD_CACHE_REDIS_HOST`               | `localhost`                                        | Redis host                              |
| `IQSCAFFOLD_CACHE_REDIS_PORT`               | `6379`                                             | Redis port                              |
| `IQSCAFFOLD_CACHE_REDIS_PASSWORD`           | _(empty)_                                          | Redis password                          |
| `IQSCAFFOLD_MESSAGING_RABBITMQ_HOST`        | `localhost`                                        | RabbitMQ host                           |
| `IQSCAFFOLD_MESSAGING_RABBITMQ_PORT`        | `5672`                                             | RabbitMQ AMQP port                      |
| `IQSCAFFOLD_MESSAGING_RABBITMQ_USERNAME`    | `iqscaffold`                                       | RabbitMQ user                           |
| `IQSCAFFOLD_MESSAGING_RABBITMQ_PASSWORD`    | `iqkv_password`                                    | RabbitMQ password                       |
| `IQSCAFFOLD_OBJECTSTORAGE_ENDPOINT`         | `http://localhost:9000`                            | MinIO / S3 endpoint                     |
| `IQSCAFFOLD_OBJECTSTORAGE_ACCESS_KEY`       | `minioadmin`                                       | Object storage access key               |
| `IQSCAFFOLD_OBJECTSTORAGE_SECRET_KEY`       | `minioadmin`                                       | Object storage secret key               |
| `IQSCAFFOLD_OBJECTSTORAGE_BUCKET_NAME`      | `user-avatars`                                     | Bucket for avatar uploads               |
| `JWT_SECRET_KEY`                            | `change-me-in-production`                          | JWT signing secret                      |
| `SMTP_HOST`                                 | `localhost`                                        | SMTP host for transactional email       |
| `SMTP_PORT`                                 | `587`                                              | SMTP port                               |
| `EMAIL_FROM_EMAIL`                          | `noreply@iqkv.dev`                                 | Sender address                          |
| `APP_BASE_URL`                              | `https://app.iqkv.dev`                             | Frontend base URL (used in email links) |
| `IQSCAFFOLD_OBSERVABILITY_TRACING_ENDPOINT` | `http://localhost:4317`                            | OpenTelemetry OTLP endpoint             |

> Copy `.env.example` to `.env.local` / `.env.uat` / `.env.prd` and fill in values.

## Maven Commands

```bash
# Build and test (skip Checkstyle during development)
./mvnw clean verify -Dcheckstyle.skip=true

# Run tests only
./mvnw test -Dcheckstyle.skip=true

# Explicit Checkstyle check
./mvnw checkstyle:check

# Coverage report → target/site/jacoco/index.html
./mvnw jacoco:report

# Production build
./mvnw clean package -Pproduction
```

## Docker

```bash
# Build image
docker build -t ORG/REPO:latest .

# Run full stack (service + all dependencies)
docker compose -f compose.container.yaml up -d
```

## Monitoring

| Endpoint                     | Description                       |
| ---------------------------- | --------------------------------- |
| `GET /actuator/health`       | Liveness + readiness probes       |
| `GET /actuator/metrics`      | Application metrics               |
| `GET /actuator/prometheus`   | Prometheus scrape endpoint        |
| `GET /swagger-ui.html`       | API documentation                 |
| `GET /.well-known/jwks.json` | Public JWK Set for JWT validation |

A Grafana dashboard (`docs/monitoring/grafana-dashboard.json`) provides real-time visibility into service health, HTTP metrics, JVM memory, HikariCP pool usage, and business metrics (registration rate, login attempts, email verification rate).

---

<details>
  <summary><strong>✅ Pre-publish checklist (remove in final README)</strong></summary>

- [ ] Title updated and logo added
- [ ] Badges added (CI, license)
- [ ] About section completed
- [ ] API tables reflect actual endpoints and auth requirements
- [ ] JWT token structure updated to match actual claims
- [ ] Authority table matches `iqscaffold.platform.authorities.definitions`
- [ ] Bounded contexts list matches actual Spring Modulith modules
- [ ] Environment variables table is complete
- [ ] Links verified (docs, external resources)
- [ ] Guidance blocks removed before publishing

</details>

---

## 🧩 Boilerplate Architecture

- **Persistence**: Spring Data JPA + Hibernate schema-per-tenant; Liquibase manages system and per-tenant schemas; Ehcache 3 as Hibernate L2 cache
- **Security**: Spring Security + Spring OAuth2 Authorization Server; JJWT for token signing; Redis-backed JWT blacklist with TTL; account lockout; rate limiting; security audit log
- **Messaging**: RabbitMQ for async user domain events (created, verified, updated, deleted); dead-letter queue; publisher confirms
- **Email**: Thymeleaf HTML templates; Spring Mail; i18n support (en/es/fr); rate-limited resend; scheduled token cleanup
- **File storage**: MinIO / S3 via `minio-java`; Apache Tika for MIME type detection; presigned URL generation
- **Module boundaries**: Spring Modulith enforces inter-module dependencies; `ModulithTest` validates the module graph
- **Testing**: JUnit 5 + Mockito; Testcontainers (PostgreSQL); embedded Redis; Spring AMQP test; ArchUnit; Spring Modulith test; JUnit QuickCheck for property-based tests
- **Observability**: OpenTelemetry tracing (OTLP); Micrometer + Prometheus; structured JSON logging (Logstash encoder) with MDC; Grafana dashboard
- **GitHub Integration**: Issue templates, labels, Dependabot, and CI workflows
- **Quality Tools**: Checkstyle, JaCoCo, ArchUnit, oxfmt, commit convention enforcement

> See [AGENTS.md](AGENTS.md) for detailed project structure, DDD patterns, and AI agent guidelines.
