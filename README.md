# Flextuma

Flextuma is a configurable, multi-tenant messaging gateway built on Spring Boot. It serves multiple organisations from a single deployment with full data isolation, and supports SMS delivery today with WhatsApp and Email on the roadmap.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Java | 17+ |
| Docker & Docker Compose | Any recent version |
| Gradle | Provided via wrapper (`./gradlew`) |

The application requires **PostgreSQL** and **Redis** to be available before startup. These are not provisioned by the included `compose.yaml` — they must be provided externally.

---

## Getting Started

### 1. Clone the repository

```bash
git clone <repository-url>
cd flextuma
```

### 2. Configure environment variables

Create a `.env` file in the root directory or export the variables in your shell:

| Variable | Required | Default | Description |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | ✅ | — | JDBC URL, e.g. `jdbc:postgresql://host:5432/db` |
| `SPRING_DATASOURCE_USERNAME` | ✅ | — | Database username |
| `SPRING_DATASOURCE_PASSWORD` | ✅ | — | Database password |
| `SPRING_DATA_REDIS_HOST` | ✅ | — | Redis hostname |
| `SPRING_DATA_REDIS_PORT` | ❌ | `6379` | Redis port |
| `HIKARI_MAX_POOL` | ❌ | `10` | Max JDBC connection pool size |
| `SMS_PRICE_PER_SEGMENT` | ❌ | `20.0` | Price per SMS segment (in TZS) |

### 3. Build the application

```bash
./gradlew clean build -x test
```

### 4. Run with Docker Compose

```bash
docker compose up --build
```

The application starts on **http://localhost:8080**.

### 5. Local development (without Docker)

```bash
./gradlew bootRun
```

### 6. Watch mode (live rebuild)

```bash
./gradlew build -t
```

---

## Architecture Overview

Flextuma follows a layered architecture with a shared `core` library and feature-based `modules`.

```
src/main/java/com/flexcodelabs/flextuma/
├── core/
│   ├── config/          # App startup, Jackson, request logging, cookie auth config
│   ├── context/         # TenantContext (ThreadLocal — reserved, not yet active)
│   ├── annotations/     # @FeatureGate — method-level feature flag annotation
│   ├── aspects/         # FeatureGateAspect — AOP enforcement of @FeatureGate
│   ├── controllers/     # BaseController<T, S> — generic CRUD for all modules
│   ├── dtos/            # Pagination<T> response wrapper
│   ├── entities/
│   │   ├── base/        # BaseEntity, NameEntity, Owner (MappedSuperclasses)
│   │   ├── auth/        # User, Role, Privilege, Organisation
│   │   ├── connector/   # ConnectorConfig
│   │   ├── contact/     # Contact
│   │   ├── feature/     # TenantFeature — per-org feature flags
│   │   ├── metadata/    # Tag, ListEntity
│   │   └── sms/         # SmsConnector, SmsTemplate, SmsLog
│   ├── enums/           # AuthType, CategoryEnum, UserType, FilterOperator
│   ├── exceptions/      # Global exception handling
│   ├── helpers/         # Specification builder, filters, masking, template utils
│   ├── interceptors/    # Entity audit interceptor
│   ├── repositories/    # BaseRepository + all JPA repositories
│   ├── security/        # SecurityConfig, SecurityUtils, CustomSecurityExceptionHandler
│   ├── senders/         # SmsSender interface + BeemSender, NextSmsSender
│   └── services/        # BaseService<T>, SmsSenderRegistry, DataSeederService
└── modules/
    ├── auth/            # User, Role, Privilege, Organisation controllers & services
    ├── connector/       # ConnectorConfig + DataHydratorService
    ├── contact/         # Contact management
    ├── feature/         # TenantFeature — per-org feature flag management
    ├── metadata/        # Tags and Lists
    ├── notification/    # Notification management
    └── sms/             # SmsConnector, SmsTemplate controllers & services
```

---

## Core Concepts

### BaseEntity & Inheritance Chain

All entities extend one of:

| Class | Adds |
|---|---|
| `BaseEntity` | `id` (UUID), `created`, `updated`, `active`, `code` |
| `NameEntity extends BaseEntity` | `name`, `description` |
| `Owner extends BaseEntity` | `createdBy` (User), `updatedBy` (User) with `@CreatedBy` audit |

### BaseController & BaseService

Every resource gets full CRUD for free by extending these:

| HTTP Method | Endpoint | Action |
|---|---|---|
| `GET` | `/api/{resource}` | Paginated list with optional `filter` and `fields` params |
| `GET` | `/api/{resource}/{id}` | Get by ID |
| `POST` | `/api/{resource}` | Create |
| `PUT` | `/api/{resource}/{id}` | Update (null-safe partial update) |
| `DELETE` | `/api/{resource}/{id}` | Delete (with optional pre-delete validation) |

**Filter syntax:** `?filter=field:OPERATOR:value` — supports `EQ`, `NE`, `LIKE`, `ILIKE`, `IN`, `GT`, `LT`.

### Permission System

Every resource defines permission constants (`READ_*`, `ADD_*`, `UPDATE_*`, `DELETE_*`). `BaseService` checks these against the current user's granted authorities before every operation. Users with `SUPER_ADMIN` or `ALL` bypass all checks.

---

## Feature Flags

Flextuma supports per-organisation feature flags via the `@FeatureGate` AOP annotation. This lets you gate specific capabilities per tenant without a code deploy — useful for subscription tiers, beta rollouts, or temporarily suspending access.

### How it works

- Annotate any service method with `@FeatureGate("FEATURE_KEY")`
- Spring AOP intercepts the call and checks the `tenantfeature` table for the calling user's organisation
- If a record with `enabled = false` exists → `403 Forbidden` is thrown before the method runs
- If **no record exists** → the feature is **allowed** (default-open: you only need records for restrictions)
- Users with no organisation (SUPER_ADMIN, system users) always bypass the check

### Developer workflow — adding a new gated feature

**Step 1.** Pick a `SCREAMING_SNAKE_CASE` key and annotate the service method:

```java
// modules/notification/services/NotificationService.java
@Async
@FeatureGate("BULK_CAMPAIGN")
public void sendCampaign(Campaign campaign, String username) {
    // 403 thrown here automatically if org has BULK_CAMPAIGN disabled
}
```

**Step 2.** Add it to the feature keys table in this README (see below).

That's it. No DB schema changes, no config files.

---

### The two-layer access model

Feature flags and permissions work together but guard different things:

| Layer | Enforced by | Question answered |
|---|---|---|
| **Permission** | `BaseService.checkPermission()` | Does *this user's role* allow this action? |
| **Feature flag** | `@FeatureGate` AOP | Does *this organisation's plan* include this capability? |

```java
@FeatureGate("BULK_CAMPAIGN")      // ← org-level: is this feature enabled for the tenant?
public void sendCampaign(...) {
    checkPermission("SEND_BULK");  // ← user-level: does the user have the right role?
    ...
}
```

| Scenario | Result |
|---|---|
| User lacks `SEND_BULK` role | `checkPermission()` throws 403 |
| User has role, but org is restricted | `@FeatureGate` throws 403 |
| User has role AND org has feature | ✅ Proceeds |

---

### Managing flags via API

```http
### Create a restriction (disable a feature for an org)
POST /api/tenantFeatures
Content-Type: application/json

{
  "organisation": { "id": "<org-uuid>" },
  "featureKey": "WHATSAPP_SEND",
  "enabled": false
}

### Re-enable (e.g. after plan upgrade)
PUT /api/tenantFeatures/<feature-uuid>
Content-Type: application/json

{ "enabled": true }

### List all flags for inspection
GET /api/tenantFeatures?filter=organisation:EQ:<org-uuid>
```

---

### Available feature keys

Document every key here when you introduce it:

| Key | Controls | Default |
|---|---|---|
| `BULK_CAMPAIGN` | Bulk messaging to contact lists/tags | Open |
| `WHATSAPP_SEND` | WhatsApp channel sending | Open |
| `EMAIL_SEND` | Email channel sending | Open |
| `CONNECTOR_PULL` | Fetching contacts via external connector | Open |

> **Convention:** All features are open by default. Only create `TenantFeature` records when you need to *restrict* an org. This keeps the table minimal and the logic simple.

---


## Modules

### Auth (`/api/users`, `/api/roles`, `/api/privileges`, `/api/organisations`)

Manages users, roles, privilege-based RBAC, and organisation membership.

- **`User`** — linked to an `Organisation` (one-to-many: many users per org). `UserType` enum (e.g. `SYSTEM`) identifies platform-level admins.
- **`Organisation`** — the multi-tenancy anchor. Each SACCO is one Organisation. All users of that SACCO share the same `organisationId`.
- **`Role`** → **`Privilege`** — fine-grained permission strings enforced in `BaseService`.

### Connector (`/api/connectorConfigs`)

Configures how Flextuma connects to each organisation's external ERP/data source.

- **`ConnectorConfig`** — stores the base URL, endpoint, `AuthType` (`NONE`, `BASIC`, `BEARER`, `API_KEY`), credentials (masked in responses), and a **JSONPath mapping list** (`List<FieldMapping>`) stored as JSONB.
- **`DataHydratorService`** — given a `tenantId` and a `memberId`, fetches the external ERP, applies the JSONPath mappings, and returns a `Map<String, String>` of system keys to values. Used to populate SMS template placeholders.

### SMS (`/api/smsConnectors`, `/api/templates`)

Manages SMS provider configurations and message templates.

- **`SmsConnector`** — provider configuration (URL, API key/secret, sender ID, extra settings). One connector can be marked active at a time.
- **`SmsTemplate`** — message templates with `{placeholder}` variables, categorised by `CategoryEnum` (`PROMOTIONAL`, etc.). System templates are protected from deletion.
- **`SmsLog`** — records every sent message: recipient, content, status, provider response, error, and linked template.
- **`SmsSenderRegistry`** — selects the active `SmsConnector` from the DB, finds the matching `SmsSender` implementation by provider name, and dispatches the message.

### SMS Providers

Two concrete `SmsSender` implementations:

| Provider | Class | Auth Method |
|---|---|---|
| **Beem** | `BeemSender` | API key + secret (Basic Auth header) |
| **NextSMS** | `NextSmsSender` | Stub (logs output — for local testing) |

Adding a new provider: implement `SmsSender`, annotate with `@Service`, and set the matching `provider` string on the `SmsConnector` record.

### Connector Module — Data Hydration Flow

```
Request with memberId
    → ConnectorConfigRepository.findByTenantId(tenantId)
    → Build URL: config.url + config.endpoint.replace("{id}", memberId)
    → Apply auth headers (BEARER / API_KEY / BASIC / NONE)
    → Parse JSON response with Jayway JsonPath
    → Map to internal keys via FieldMapping list
    → Return Map<String, String> for template rendering
```

---

## Security

### Authentication

| Client | Method |
|---|---|
| Browser / SPA | Session-based: POST credentials to `/api/login` → receive HttpOnly `SESSION` cookie (backed by Redis) |
| API/testing | HTTP Basic Auth (`Authorization: Basic base64(user:pass)`) — also accepted for session creation |
| Webhooks / PAT | Personal Access Token (planned) |

### CSRF

CSRF protection uses `CookieCsrfTokenRepository` (token sent as `XSRF-TOKEN` cookie, readable by SPA). Exemptions:

- `/api/login` — no session exists yet at this point
- `/api/webhooks/**` — reserved for PAT-authenticated provider callbacks

### Tenant-Aware Resource Filtering

Every paginated and list query automatically applies `TenantAwareSpecification`:

| User | Sees |
|---|---|
| `SUPER_ADMIN` or `ALL` authority | All records (no restriction) |
| User with an Organisation | Records they created **or** records created by any member of the same organisation |
| User with no Organisation | Only their own records |
| Entities without `createdBy` (e.g. `Organisation`) | No restriction applied |

This is enforced in `BaseService.buildTenantSpec()` — all subclass services benefit automatically.

### Session Management

- Sessions are stored in **Redis** (`@EnableRedisHttpSession`)
- Session cookie: `SESSION`, HttpOnly, `SameSite=Lax`
- Maximum **1 concurrent session** per user

---

## Data Seeding

On startup, `DataInitializer` runs `DataSeederService.seedSystemData()`, which executes `seed.sql` via JDBC to ensure system-level data (privileges, default roles, system user) is present before the application accepts requests.

---

## Development Guide

### Running tests

```bash
./gradlew test
```

### API testing (`.http` files)

HTTP request files are in the `/http` directory. Use IntelliJ's HTTP client or any compatible tool. The login endpoint does not require a CSRF token. All subsequent mutating requests (`POST`/`PUT`/`DELETE`) must include the `X-CSRF-TOKEN` header (value from the `XSRF-TOKEN` response cookie).

```http
### Login
POST http://localhost:8080/api/login
Content-Type: application/json

{"username": "admin", "password": "pass"}
```

### Adding a new module

1. Create an entity in `core/entities/` extending `BaseEntity`, `NameEntity`, or `Owner`
2. Define permission constants (`READ_*`, `ADD_*`, etc.) on the entity
3. Create a `JpaRepository` in `core/repositories/`
4. Create a `Service extends BaseService<YourEntity>` in `modules/.../services/`
5. Create a `Controller extends BaseController<YourEntity, YourService>` in `modules/.../controllers/`

---

## Roadmap

See [`ROADMAP/roadmap.md`](ROADMAP/roadmap.md) for the full development roadmap, [`ROADMAP/architecture.md`](ROADMAP/architecture.md) for the multi-channel notification architecture, and [`ROADMAP/roadmap-audit.md`](ROADMAP/roadmap-audit.md) for the current implementation status of each item.

**Recently completed:**
- [x] Per-organisation feature flagging via `@FeatureGate` AOP annotation
- [x] `TenantAwareSpecification` — automatic org-scoped data isolation
- [x] `DataHydratorService` — external ERP integration with JSONPath field mapping
- [x] Template placeholder engine (`{{variable}}` syntax with missing-variable detection)
- [x] SMS segment calculator (GSM-7 vs Unicode encoding)
- [x] Wallet & ledger system with pre-flight balance checks
- [x] Async SMS dispatch worker (`@Scheduled` + `SmsLog` status lifecycle)
- [x] Rate Limiter (Bucket4j per-tenant quotas)
- [x] Webhook DLR receiver & Recipient Resolver Trigger API (`/api/webhooks...`)
- [x] Character Count & Preview API (`/api/smsTemplates/preview`)

**Immediate next steps:**
- [ ] Admin Monitoring API enhancements (query by status, retry endpoint)
- [ ] Scheduling Engine (future-dated campaigns)
- [ ] Personal Access Token (PAT) entity and filter for API / gateway access

---

## Wallet Management Example
The new `WalletService` handles crediting and debiting of accounts per organisation. 
Currently, wallets must be topped up programmatically until an admin UI is built.

Example of topping up an account with 100,000 TZS dynamically inside a Service:

```java
@Autowired
private WalletService walletService;

public void processManualTopup(User orgAdmin) {
    BigDecimal amount = BigDecimal.valueOf(100000.00);
    walletService.credit(orgAdmin, amount, "Manual Top Up", "REF-12345");
}
```
