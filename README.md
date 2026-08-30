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
| `SPRING_DATA_REDIS_HOST` | ❌ | `redis` | Redis hostname; used when `REDIS_URL` is not set |
| `SPRING_DATA_REDIS_PORT` | ❌ | `6379` | Redis port |
| `REDIS_URL` | ❌ | — | Full Redis URL (for example, `redis://:password@redis:6379/0`); overrides host and port |
| `HIKARI_MAX_POOL` | ❌ | `10` | Max JDBC connection pool size |
| `SMS_PRICE_PER_SEGMENT` | ❌ | `20.0` | Price per SMS segment (in TZS) |
| `FLEXTUMA_SMS_BEEM_DELIVERY_POLL_INTERVAL_MS` | ❌ | `60000` | Beem delivery-report polling interval in milliseconds |
| `FLEXTUMA_SMS_BEEM_DELIVERY_MINIMUM_DELAY_MINUTES` | ❌ | `5` | Minimum delay before the first Beem delivery lookup |

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

Base URL: `http://localhost:8080`. All API routes return JSON unless noted. Substitute real UUIDs for `{id}`. Authentication is required for every API route except registration, login, and `POST /api/webhooks/{provider}`. Use the `SESSION` cookie returned by login, or send a personal access token as `X-API-KEY: <token>`.

### Common response and error formats

Successful entity responses include the entity's `id`, audit timestamps, and the fields shown in the request. A representative error response is:

```json
{
  "timestamp": "2026-08-16T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed"
}
```

### Authentication

| Endpoint | Sample request | Sample response |
|---|---|---|
| `POST /api/register` | `{"name":"Jane Doe","username":"jane","password":"Secret123!","phoneNumber":"+255700000000","email":"jane@example.com"}` | `201` — `{"id":"…","username":"jane","email":"jane@example.com","verified":false}` |
| `POST /api/login` | `{"username":"jane","password":"Secret123!"}` | `200` — `{"id":"…","username":"jane","email":"jane@example.com"}` and a `Set-Cookie: SESSION=…` header |
| `POST /api/logout` | no body | `200` — `{"message":"Logged out successfully"}` |
| `GET /api/me` | no body | `200` — `{"id":"…","username":"jane","email":"jane@example.com","roles":[]}` |
| `POST /api/verify` | `{"identifier":"jane@example.com","code":"123456"}` | `200` — `{"success":true,"data":"Verification successful"}` |
| `POST /api/resendVerification` | `{"identifier":"jane@example.com"}` | `200` — `{"success":true,"data":"Verification code sent"}` |
| `POST /api/changePassword` | `{"currentPassword":"Secret123!","newPassword":"NewSecret123!","confirmPassword":"NewSecret123!"}` | `200` — `{"success":true,"data":{"id":"…","username":"jane"}}` |

Login and registration are rate limited. `POST /api/changePassword` and `GET /api/me` require authentication.

### Shared CRUD endpoints

The following resource paths implement every operation in this table: `/api/users`, `/api/roles`, `/api/privileges`, `/api/organisations`, `/api/tokens`, `/api/connectorConfigs`, `/api/contacts`, `/api/tenantFeatures`, `/api/wallets`, `/api/lists`, `/api/tags`, `/api/personalNotifications`, `/api/campaigns`, `/api/connectors`, `/api/smsLogs`, and `/api/templates`.

| Endpoint | Sample request | Sample response |
|---|---|---|
| `GET {resource}?page=0&size=20&sort=createdAt,desc` | no body | `200` — `{"page":1,"total":1,"pageSize":20,"contacts":[{"id":"…","firstName":"Jane"}]}` |
| `GET {resource}/{id}` | no body | `200` — `{"id":"…","firstName":"Jane","phoneNumber":"+255700000000"}` |
| `GET {resource}/fields` | no body | `200` — `[{"name":"firstName","type":"String","mandatory":false}]` |
| `GET {resource}/aggregate?aggregate=count(id):total&groupBy=status` | no body | `200` — `[{"status":"ACTIVE","total":12}]` |
| `POST {resource}` | JSON body from the resource examples below | `200` — the created entity, for example `{"id":"…","firstName":"Jane","phoneNumber":"+255700000000"}` |
| `PUT {resource}/{id}` | JSON body from the resource examples below | `200` — the updated entity |
| `DELETE {resource}/{id}` | no body | `200` — `{"message":"contacts deleted successfully"}` |
| `DELETE {resource}/bulky?filter=status:eq:INACTIVE` | no body | `200` — `{"message":"3 contacts deleted successfully"}` |

For list and aggregate operations, repeat `filter` to apply multiple filters, use `rootJoin=OR` to join filters with OR (the default is `AND`), and use `fields=field1,field2` to select fields. Aggregates use `function(column):alias`, such as `sum(balance):total`. The resource-specific array key in a list response is the resource name (for example `smsLogs`, `tenantFeatures`, or `connectorConfigs`).

#### Resource request bodies

These are `POST` and `PUT` request samples for each shared CRUD resource; the corresponding response is the saved object, with an `id` and audit fields added.

| Resource | Sample request body |
|---|---|
| `/api/users` | `{"name":"Jane Doe","username":"jane","email":"jane@example.com","phoneNumber":"+255700000000","password":"Secret123!","roles":[{"id":"…"}],"organisation":{"id":"…"}}` |
| `/api/roles` | `{"name":"OPERATOR","privileges":[{"id":"…"}]}` |
| `/api/privileges` | `{"name":"View contacts","value":"CONTACT_READ","system":false}` |
| `/api/organisations` | `{"name":"Acme Ltd","phoneNumber":"+255700000000","email":"ops@acme.example","address":"Dar es Salaam","website":"https://acme.example"}` |
| `/api/tokens` | `{"name":"integration","user":{"id":"…"},"expiresAt":"2027-01-01T00:00:00"}` |
| `/api/connectorConfigs` | `{"tenantId":"tenant-1","url":"https://erp.example","endpoint":"/customers","search":"status=ACTIVE","authType":"BEARER","token":"secret","mappings":[{"systemKey":"phoneNumber","jsonPath":"phone"}]}` |
| `/api/contacts` | `{"firstName":"Jane","middleName":"A","surname":"Doe","email":"jane@example.com","phoneNumber":"+255700000000","status":"ACTIVE","lists":[{"id":"…"}],"tags":[{"id":"…"}]}` |
| `/api/tenantFeatures` | `{"organisation":{"id":"…"},"featureKey":"SMS","enabled":true}` |
| `/api/wallets` | `{"balance":1000.00,"smsCost":20.00,"currency":"TZS","type":"SMS"}` |
| `/api/lists` | `{"name":"Customers","description":"Subscribed customers"}` |
| `/api/tags` | `{"name":"VIP","description":"High-value contact"}` |
| `/api/personalNotifications` | `{"title":"Low balance","message":"Top up your wallet","type":"WARNING","linkUrl":"/wallet","metadata":"{}"}` |
| `/api/campaigns` | `{"name":"August promotion","description":"Monthly offer","template":{"id":"…"},"scheduledAt":"2026-08-20T10:30:00","status":"SCHEDULED","recipients":"+255700000000,+255700000001","connector":{"id":"…"}}` |
| `/api/connectors` | `{"provider":"beem","url":"https://apisms.beem.africa","key":"api-key","secret":"api-secret","senderId":"FLEXTUMA","isDefault":true,"extraSettings":"{}"}` |
| `/api/smsLogs` | `{"recipient":"+255700000000","content":"Hello Jane","status":"PENDING","connector":{"id":"…"},"scheduledAt":"2026-08-20T10:30:00"}` |
| `/api/templates` | `{"code":"WELCOME","name":"Welcome","description":"New-user greeting","content":"Hello {{name}}","category":"PROMOTIONAL","system":false}` |

`POST /api/tokens` returns the token entity; retain the returned `rawToken` securely because it is the value to pass in `X-API-KEY`. It must not be logged or committed.

### SMS, notifications, and dashboard

| Endpoint | Sample request | Sample response |
|---|---|---|
| `POST /api/templates/preview` | `{"template":"Hello {{name}}, your order {{orderId}} is ready!","variables":{"name":"Jane","orderId":"12345"}}` | `200` — `{"renderedContent":"Hello Jane, your order 12345 is ready!","segmentCount":1,"encoding":"GSM-7","charactersRemaining":121,"cost":20.00,"pricePerSegment":20.00}` |
| `POST /api/smsLogs/{id}/retry` | no body | `200` — `{"id":"…","recipient":"+255700000000","status":"PENDING","retries":1}` |
| `GET /api/notifications?page=1&pageSize=15` | no body | `200` — `{"page":1,"total":1,"pageSize":15,"data":[{"id":"…","phoneNumber":"+255700000000","message":"Hello Jane","status":"PENDING","provider":"beem"}]}` |
| `POST /api/notifications` | `{"phoneNumber":"+255700000000","templateCode":"WELCOME","provider":"beem","name":"Jane"}` | `200` — `{"id":"…","recipient":"+255700000000","content":"Hello Jane","status":"PENDING"}` |
| `POST /api/notifications/raw` | `{"phoneNumber":"+255700000000","message":"Direct message","provider":"beem"}` | `200` — `{"id":"…","recipient":"+255700000000","content":"Direct message","status":"PENDING"}` |
| `GET /api/personalNotifications/summary?pageSize=5` | no body | `200` — `{"unreadCount":2,"notifications":[{"id":"…","title":"Low balance","readAt":null}]}` |
| `POST /api/personalNotifications/{id}/read` | no body | `200` — `{"id":"…","title":"Low balance","readAt":"2026-08-16T12:00:00"}` |
| `POST /api/personalNotifications/readAll` | no body | `200` — `{"updated":2}` |
| `GET /api/dashboard/summary` | no body | `200` — `{"userId":"…","username":"jane","sent":12,"failed":1,"balanceAmount":980.00,"balance":"980.00","currency":"TZS","activeCampaigns":1,"today":3,"thisWeek":12,"thisMonth":13,"successRate":92.31,"statusBreakdown":{"sent":92.31,"failed":7.69,"pending":0.0,"other":0.0}}` |

### Webhooks

`POST /api/webhooks/{provider}` is unauthenticated; all other webhook routes require authentication.

| Endpoint | Sample request | Sample response |
|---|---|---|
| `POST /api/webhooks/beem` | `{"request_id":"provider-message-id","status":"DELIVERED","timestamp":"2026-08-16T12:00:00Z"}` | `200` with an empty body |
| `POST /api/webhooks/{connectorConfigId}/sms` | `{"provider":"beem","templateCode":"WELCOME","filterQuery":{"status":"ACTIVE"}}` | `200` — `{"message":"Successfully queued messages","queued":10,"totalFetched":10}` |
| `POST /api/webhooks/{connectorConfigId}/sms` (raw) | `{"provider":"beem","content":"Custom message","filterQuery":{"status":"ACTIVE"}}` | `200` — `{"message":"Successfully queued messages","queued":10,"totalFetched":10}` |

For Beem, delivery reports are normally obtained by the polling worker, which uses the submitted `request_id` saved as `providerMessageId`.

### System administration

These routes require `SUPER_ADMIN` or `ALL` authority.

| Endpoint | Sample request | Sample response |
|---|---|---|
| `GET /api/systemLogs?page=0&size=20&level=ERROR&source=SMS&traceId=abc&from=2026-08-01T00:00:00&to=2026-08-16T23:59:59` | no body | `200` — `{"page":1,"total":1,"pageSize":20,"systemLog":[{"id":"…","level":"ERROR","source":"SMS","message":"Delivery failed"}]}` |
| `GET /api/systemLogs/tail?level=ERROR` | Header: `Accept: text/event-stream` | `200`, SSE stream such as `data:{"level":"ERROR","message":"Delivery failed"}` |
| `GET /api/systemLogs/health` | no body | `200` — `{"status":"ONLINE","uptime":"0d 1h 2m 3s","uptimeMs":3723000,"memory":{"totalMb":256,"freeMb":120,"usedMb":136,"maxMb":512},"activeThreads":24,"availableProcessors":8,"retentionDays":30}` |
| `DELETE /api/systemLogs/purge?days=30` | no body | `200` — `{"message":"42 log entries purged","olderThanDays":30}` |

### App upload

`POST /api/apps` requires `SUPER_ADMIN` authority and accepts `multipart/form-data`.

```bash
curl -X POST http://localhost:8080/api/apps \
  -H 'X-API-KEY: <token>' \
  -F 'zipFile=@application.zip' \
  -F 'appName=myapp' \
  -F 'version=1.0.0' \
  -F 'overwrite=true'
```

Example response (`200`):

```json
{
  "message": "Application uploaded and extracted successfully",
  "appName": "myapp",
  "version": "1.0.0",
  "extractedPath": "/tmp/apps/myapp/1.0.0",
  "fileSize": 2048,
  "extractedFiles": 12
}
```

### Frontend/static routes

| Endpoint | Sample request | Sample response |
|---|---|---|
| `GET /` | Header: `Accept: text/html` | `200` — the frontend `index.html` document |
| `GET /assets/{filename}` | `GET /assets/app.js` | `200` — the requested static asset, for example JavaScript with `Content-Type: application/javascript` |
| `GET /**` | `GET /campaigns` with `Accept: text/html` | `200` — the frontend `index.html` SPA shell; non-HTML unknown paths return `404` |

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

CSRF protection is disabled. Authenticate browser requests with the `SESSION` cookie and API requests with `X-API-KEY`.

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

### Auth (`/api/users`, `/api/roles`, `/api/privileges`, `/api/organisations`, `/api/tokens`)

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
- Template preview with cost calculation (`/api/templates/preview`)
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
| **Beem** | `BeemSender` + `BeemDeliveryReportWorker` | API key + secret (HTTP Basic); delivery-report polling | ✅ Production ready |
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
| API clients | Personal access token in the `X-API-KEY` header |
| Webhooks | Unauthenticated provider callback (`POST /api/webhooks/{provider}`) |

### CSRF

CSRF protection is disabled. Browser clients authenticate with the HttpOnly session cookie; API clients can use `X-API-KEY`.

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

HTTP request files are in the `/http` directory. Use IntelliJ's HTTP client or any compatible tool. CSRF is disabled, so no `X-CSRF-TOKEN` header is needed; authenticate subsequent requests with the session cookie or `X-API-KEY`.

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
- [x] Character Count & Preview API (`/api/templates/preview` returning segment counts and `charactersRemaining` budget)
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
