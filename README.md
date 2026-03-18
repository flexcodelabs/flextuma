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

## API Reference

### Authentication & Authorization

#### User Registration
```http
POST /api/register
Content-Type: application/json

{
  "username": "string",
  "email": "string", 
  "phoneNumber": "string",
  "password": "string",
  "organisation": { "id": "uuid" }
}
```
- **Rate Limited**: Prevents brute force registration attempts
- **Verification**: Automatically sends verification codes to email and phone
- **Bonus Credits**: Awards configurable SMS segments on successful registration

#### User Login
```http
POST /api/login
Content-Type: application/json

{
  "username": "string",
  "password": "string"
}
```
- **Rate Limited**: Blocks excessive login attempts
- **Session Management**: Creates HttpOnly session cookie backed by Redis
- **Security Logging**: Records all login attempts for audit

#### User Logout
```http
POST /api/logout
```
- Clears session cookie and logs security event

#### Current User Profile
```http
GET /api/me
```
- Returns authenticated user's profile information

#### Email/Phone Verification
```http
POST /api/verify
Content-Type: application/json

{
  "identifier": "email@domain.com",
  "code": "123456"
}
```

```http
POST /api/resendVerification
Content-Type: application/json

{
  "identifier": "email@domain.com"
}
```

#### Password Change
```http
POST /api/changePassword
Content-Type: application/json

{
  "currentPassword": "string",
  "newPassword": "string", 
  "confirmPassword": "string"
}
```

#### Personal Access Tokens (PAT)
```http
# Standard CRUD operations
GET|POST|PUT|DELETE /api/personalAccessTokens
```
- Enables API authentication without session cookies
- Ideal for integrations and automated systems

---

### SMS Campaigns (`/api/campaigns`)

#### Campaign Management
Full CRUD operations for SMS campaigns with scheduling capabilities:

```json
{
  "name": "Campaign Name",
  "description": "Campaign description", 
  "template": { "id": "template-uuid" },
  "scheduledAt": "2024-01-15T10:30:00",
  "status": "DRAFT|SCHEDULED|RUNNING|COMPLETED",
  "recipients": "phone1,phone2,phone3",
  "connector": { "id": "connector-uuid" }
}
```

#### Campaign Status Flow
1. **DRAFT** - Initial state, can be modified
2. **SCHEDULED** - Set for future delivery
3. **RUNNING** - Currently being processed
4. **COMPLETED** - Finished processing

---

### Finance & Wallet Management (`/api/wallets`)

#### Wallet Operations
```json
{
  "balance": 1000.0000,
  "smsCost": 20.00,
  "currency": "TZS",
  "type": "SMS",
  "value": 20000.00
}
```

#### Features
- **Multi-currency Support** - Configurable currency per wallet
- **Real-time Balance** - Updated immediately after SMS sending
- **Cost Tracking** - Per-segment cost calculation
- **Transaction History** - Complete audit trail via WalletTransaction

#### Automatic Credit Allocation
- Registration bonus credits configurable via environment
- Pre-flight balance checks before SMS sending
- Automatic deduction upon successful delivery

---

### Advanced SMS Features

#### Template Preview & Cost Calculation
```http
POST /api/smsTemplates/preview
Content-Type: application/json

{
  "template": "Hello {{name}}, your order {{orderId}} is ready!",
  "variables": {
    "name": "John Doe",
    "orderId": "12345"
  }
}
```

**Response:**
```json
{
  "rendered": "Hello John Doe, your order 12345 is ready!",
  "segments": 1,
  "encoding": "GSM-7",
  "charactersRemaining": 145,
  "cost": 20.00,
  "pricePerSegment": 20.00
}
```

#### Failed Message Retry
```http
POST /api/smsLogs/{id}/retry
```
- Retries failed SMS messages with original parameters
- Updates log status and provider response

---

### System Administration (`/api/systemLogs`)

#### Log Query & Filtering
```http
GET /api/systemLogs?level=ERROR&source=SMS&from=2024-01-01T00:00:00
```

**Parameters:**
- `level` - Log level (ERROR, WARN, INFO, DEBUG)
- `source` - Component source (SMS, AUTH, WEBHOOK, etc.)
- `traceId` - Request trace identifier
- `from/to` - Date range filtering

#### Real-time Log Streaming
```http
GET /api/systemLogs/tail?level=ERROR
Accept: text/event-stream
```
- Server-Sent Events (SSE) for live log monitoring
- Filterable by log level

#### System Health Monitoring
```http
GET /api/systemLogs/health
```
Returns system health metrics including database status, memory usage, and active connections.

#### Log Maintenance
```http
DELETE /api/systemLogs/purge?days=30
```
- Purges log entries older than specified days
- Returns count of deleted records

---

### Webhooks & Integrations (`/api/webhooks`)

#### Delivery Report (DLR) Receiver
```http
POST /api/webhooks/{provider}
Content-Type: application/json

{
  "messageId": "provider-message-id",
  "status": "DELIVERED|FAILED|PENDING",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

**Supported Providers:**
- `beem` - Beem SMS provider
- `next` - NextSMS provider

#### Recipient Resolver Trigger
```http
POST /api/webhooks/{connectorId}/sms
Content-Type: application/json

{
  "provider": "beem",
  "templateCode": "WELCOME_MSG",
  "content": "Custom message content",
  "filterQuery": "status=active"
}
```

**Features:**
- Fetches recipients from external ERP systems
- Supports both template-based and raw content
- Automatic queueing for async processing

---

### Notification System (`/api/notifications`)

#### Template-based SMS
```http
POST /api/notifications
Content-Type: application/json

{
  "to": "+255123456789",
  "templateCode": "WELCOME_MSG", 
  "variables": {
    "name": "John Doe",
    "company": "ACME Corp"
  }
}
```

#### Raw SMS Sending
```http
POST /api/notifications/raw
Content-Type: application/json

{
  "to": "+255123456789",
  "content": "Direct message content",
  "provider": "beem"
}
```

---

### App Management (`/api/apps`)

#### Application Upload
```http
POST /api/apps
Content-Type: multipart/form-data

appName: myapp
version: 1.0.0
file: [application.zip]
```
- **SUPER_ADMIN** only endpoint
- Uploads and extracts application packages
- Supports system extensions and plugins

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

## Security

### Authentication Methods

| Client Type | Method | Usage |
|---|---|---|
| **Browser/SPA** | Session-based auth via `POST /api/login` | Receives HttpOnly `SESSION` cookie (Redis-backed) |
| **API/Testing** | HTTP Basic Auth (`Authorization: Basic base64(user:pass)`) | Also creates session for convenience |
| **Integrations** | Personal Access Token (PAT) | Token-based auth for automated systems |

### Rate Limiting

**Authentication Endpoints:**
- **Registration**: Blocks after excessive attempts with configurable timeout
- **Login**: Prevents brute force attacks with progressive delays
- **Verification**: Limits resend attempts to prevent abuse

**Rate Limit Response:**
```json
{
  "error": "Rate limit exceeded",
  "message": "Too many attempts. Try again in 300 seconds.",
  "retryAfter": 300
}
```

### CSRF Protection

- **Token Method**: Cookie-based `XSRF-TOKEN` + header `X-CSRF-TOKEN`
- **Exemptions**: `/api/login`, `/api/webhooks/**` (for provider callbacks)
- **Browser Support**: Automatic for modern SPAs using `withCredentials: true`

### Session Management

- **Storage**: Redis-based session persistence
- **Cookie**: `SESSION`, HttpOnly, `SameSite=Lax`
- **Concurrency**: Maximum 1 concurrent session per user
- **Timeout**: Configurable session expiration

### Security Event Logging

All security events are automatically logged:
- Login attempts (success/failure)
- Registration attempts
- Password changes
- Logout events
- Verification attempts

**Log Format:**
```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "level": "INFO",
  "source": "AUTH",
  "event": "LOGIN_SUCCESS",
  "username": "john.doe",
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  "traceId": "abc123-def456"
}
```

### Data Isolation

**Tenant-Aware Filtering:**
- **SUPER_ADMIN/ALL**: Sees all records (no restriction)
- **Organisation Users**: Sees own records + org-wide data
- **Unaffiliated Users**: Sees only personal records
- **System Entities**: No filtering applied (e.g., Organisation)

**Implementation:** Automatic `TenantAwareSpecification` in `BaseService`

---

## Modules

### Auth (`/api/users`, `/api/roles`, `/api/privileges`, `/api/organisations`, `/api/personalAccessTokens`)

Manages users, roles, privilege-based RBAC, organisation membership, and API tokens.

- **`User`** — linked to an `Organisation` (one-to-many: many users per org). `UserType` enum (e.g. `SYSTEM`) identifies platform-level admins.
- **`Organisation`** — the multi-tenancy anchor. Each SACCO is one Organisation. All users of that SACCO share the same `organisationId`.
- **`Role`** → **`Privilege`** — fine-grained permission strings enforced in `BaseService`.
- **`PersonalAccessToken`** — API tokens for integrations and automated systems.

**Additional Endpoints:**
- `/api/register` - User registration with verification
- `/api/login` - Authentication with rate limiting
- `/api/logout` - Session termination
- `/api/me` - Current user profile
- `/api/verify` - Email/phone verification
- `/api/changePassword` - Password management

### Finance (`/api/wallets`)

Manages organizational wallets and SMS billing.

- **`Wallet`** — per-organisation SMS credit balance with real-time updates
- **`WalletTransaction`** — complete audit trail of all credit movements

**Features:**
- Multi-currency support (TZS, USD, etc.)
- Per-segment cost calculation
- Automatic credit deduction on SMS delivery
- Registration bonus credit allocation
- Pre-flight balance checks

### SMS (`/api/smsConnectors`, `/api/templates`, `/api/campaigns`, `/api/smsLogs`)

Comprehensive SMS management with campaigns, templates, and delivery tracking.

- **`SmsConnector`** — provider configuration (URL, API key/secret, sender ID, extra settings). One connector can be marked active at a time.
- **`SmsTemplate`** — message templates with `{placeholder}` variables, categorised by `CategoryEnum` (`PROMOTIONAL`, etc.). System templates are protected from deletion.
- **`SmsLog`** — records every sent message: recipient, content, status, provider response, error, and linked template.
- **`SmsCampaign`** — scheduled bulk messaging with status tracking (DRAFT, SCHEDULED, RUNNING, COMPLETED).
- **`SmsSendResult`** — standardized result object containing success/failure status, message ID, error codes, and full provider response data.
- **`SmsSenderRegistry`** — selects the active `SmsConnector` from the DB, finds the matching `SmsSender` implementation by provider name, and dispatches the message.

**Advanced Features:**
- Template preview with cost calculation (`/api/smsTemplates/preview`)
- Failed message retry (`/api/smsLogs/{id}/retry`)
- Character encoding detection (GSM-7 vs UCS-2)
- Segment-based billing

### Notification (`/api/notifications`)

Real-time notification dispatch and queue management.

- **Template-based SMS** - Send templated messages with variable substitution
- **Raw SMS Sending** - Direct content delivery without templates
- **Queue Management** - Async processing with status tracking

### System Administration (`/api/systemLogs`, `/api/apps`)

System monitoring, logging, and application management.

- **`SystemLog`** — structured logging with filtering, search, and real-time streaming
- **App Management** — application upload and plugin system

**Features:**
- Real-time log streaming via Server-Sent Events
- Log level filtering (ERROR, WARN, INFO, DEBUG)
- System health monitoring
- Log purging by date range
- Application package upload (SUPER_ADMIN only)

### Webhooks (`/api/webhooks`)

External integrations and delivery report handling.

- **Delivery Report (DLR) Receiver** — Accepts status updates from SMS providers
- **Recipient Resolver Trigger** — External ERP integration for bulk messaging

**Supported Providers:**
- `beem` - Beem SMS provider DLRs
- `next` - NextSMS provider DLRs

### Connector (`/api/connectorConfigs`)

Configures how Flextuma connects to each organisation's external ERP/data source.

- **`ConnectorConfig`** — stores the base URL, endpoint, `AuthType` (`NONE`, `BASIC`, `BEARER`, `API_KEY`), credentials (masked in responses), and a **JSONPath mapping list** (`List<FieldMapping>`) stored as JSONB.
- **`DataHydratorService`** — given a `tenantId` and a `memberId`, fetches the external ERP, applies the JSONPath mappings, and returns a `Map<String, String>` of system keys to values. Used to populate SMS template placeholders.

### Contact (`/api/contacts`)

Contact and recipient management for messaging campaigns.

### Feature (`/api/tenantFeatures`)

Per-organisation feature flag management for subscription tiers and access control.

### Metadata (`/api/tags`, `/api/lists`)

Tag and list management for organizing contacts and content.

### SMS Providers

Two concrete `SmsSender` implementations:

| Provider | Class | Auth Method | Status |
|---|---|---|---|
| **Beem** | `BeemSender` | API key + secret (Basic Auth header) | ✅ Production ready |
| **NextSMS** | `NextSmsSender` | API key + secret (Basic Auth header) | ✅ Production ready |

Adding a new provider: implement `SmsSender`, annotate with `@Service`, and set the matching `provider` string on the `SmsConnector` record.

### SMS Provider Response Handling

All SMS providers now return standardized `SmsSendResult` objects that include:

- **Success/Failure Status** - Boolean success flag with descriptive messages
- **Message ID** - Provider-specific message identifier for tracking
- **Error Codes** - Standardized error codes for failure scenarios
- **Full Provider Response** - Complete response data as `Map<String, Object>` for debugging and audit

**Response Processing Flow:**
```
Provider HTTP Response → SmsSender.processResponse() → SmsSendResult → SmsLog.providerResponse
```

**Key Features:**
- Type-safe response mapping using Jackson `Map<String, Object>` conversion
- Automatic error extraction from provider error responses
- Detailed logging of provider responses for audit trails
- Consistent error handling across all SMS providers

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
- [x] Admin Monitoring API enhancements (query by status, retry endpoint)
- [x] Scheduling Engine (future-dated campaigns)
- [x] Personal Access Token (PAT) entity and filter for API / gateway access
- [x] Per-organisation feature flagging via `@FeatureGate` AOP annotation
- [x] `TenantAwareSpecification` — automatic org-scoped data isolation
- [x] `DataHydratorService` — external ERP integration with JSONPath field mapping
- [x] Template placeholder engine (`{{variable}}` syntax with missing-variable detection)
- [x] SMS segment calculator (GSM-7 vs Unicode encoding)
- [x] Wallet & ledger system with pre-flight balance checks
- [x] Async SMS dispatch worker (`@Scheduled` + `SmsLog` status lifecycle)
- [x] Rate Limiter (Bucket4j per-tenant quotas)
- [x] Webhook DLR receiver & Recipient Resolver Trigger API (`/api/webhooks...`)
- [x] Character Count & Preview API (`/api/smsTemplates/preview` returning segment counts and `charactersRemaining` budget)
- [x] Real HTTP implementation for `NextSmsSender` with provider response logging
- [x] Standardized `SmsSendResult` service with type-safe response handling

**Immediate next steps:**
- [ ] Database Partitioning for `sms_log` table
- [ ] Multi-channel support (WhatsApp/Email)

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
