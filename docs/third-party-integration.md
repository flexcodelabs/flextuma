# Flextuma third-party integration guide and gap analysis

## Integration surfaces

Flextuma currently integrates with:

| Party | Direction | Mechanism |
| --- | --- | --- |
| BEEM SMS | Outbound SMS and delivery-report polling | JSON over HTTPS using HTTP Basic credentials. |
| NextSMS | Outbound SMS; intended inbound delivery reports | JSON over HTTPS using Basic authentication. |
| Tenant/customer data API | Outbound recipient lookup and member hydration | Configurable GET requests plus JSONPath field mapping. |
| Client/automation | Inbound API requests | Session cookie or personal access token (PAT) in `X-API-KEY`. |

There is no OpenAPI document or API version prefix. Treat the current API as an internal integration contract and pin integrations to an application release until a versioning policy is introduced.

## API authentication for clients

Create a Personal Access Token through the authenticated CRUD endpoint `POST /api/tokens`. A newly generated raw token is exposed through the entity’s transient `rawToken` field, so capture it at creation time; only its SHA-256 hash is persisted. Send it on every automation request:

```http
X-API-KEY: ft_<token-value>
```

PATs act as the owning user and inherit that user’s privileges. Use one token per integration, give it an expiry, rotate it, and disable/delete it when no longer needed. Do not put PATs in browser code, query strings, logs, or support tickets.

All API paths other than login, registration, frontend assets, and the one-segment DLR callback path (`POST /api/webhooks/{provider}`) require authentication under the active security configuration. There is no separate, functioning API-key scheme for anonymous webhooks despite the `flextuma.auth.api-key-endpoints` configuration property.

## SMS provider setup

Create an SMS connector using `POST /api/connectors` with the provider string, provider endpoint, credentials, and sender ID. The provider names implemented by the service are `BEEM` and `NEXT` (case-insensitive at send time). Connector key and secret fields are write-only/masked in API responses; retain the original values in your secret manager.

### BEEM

Configure the connector with `provider: "BEEM"`, URL `https://apisms.beem.africa/v1/send`, the BEEM API key in `key`, the BEEM secret key in `secret`, and an active BEEM sender ID in `senderId`. The adapter sends HTTP Basic authentication (`key:secret`) and this JSON body:

```json
{
  "source_addr": "ACME",
  "schedule_time": "",
  "encoding": "0",
  "message": "Hello world",
  "recipients": [{ "recipient_id": "1", "dest_addr": "255700000001" }]
}
```

Set optional Beem request fields through `extraSettings`, for example `{"encoding":"0","schedule_time":"2026-08-15 10:30"}`. `schedule_time` is GMT+0 in `yyyy-mm-dd hh:mm` format. Beem returns `request_id`; Flextuma stores it as `providerMessageId` for delivery tracking.

### NextSMS

The NextSMS adapter sends `{ "from", "to", "text" }` as JSON and uses HTTP Basic authentication (`key:secret`). It records `messages[0].messageId` when provided. Configure the exact NextSMS endpoint and approved sender ID from the provider account.

### Delivery reports (DLRs)

#### BEEM polling (primary)

Beem’s documented delivery mechanism is polling, not callback registration. Starting five minutes after a successful send, Flextuma polls the following endpoint for BEEM messages in `SENT` status:

```http
GET https://dlrapi.beem.africa/public/v1/delivery-reports?dest_addr={recipient}&request_id={providerMessageId}
Authorization: Basic base64(key:secret)
```

The polling interval defaults to 60 seconds and is configurable with `flextuma.sms.beem.delivery-poll-interval-ms`. A Beem `DELIVERED` status becomes `DELIVERED`, `UNDELIVERED` becomes `FAILED`, and `PENDING` remains `SENT` until a terminal status is returned.

#### Provider callbacks (optional)

The implemented route is:

```http
POST https://<public-host>/api/webhooks/BEEM
POST https://<public-host>/api/webhooks/NEXT
Content-Type: application/json
```

The callback path is public and accepts no custom authentication header. Beem callbacks are correlated with `request_id` (with legacy `messageID` accepted) plus `status`; Next callbacks use `message_id` (or `messageId`) plus `status`. The callback route is optional for Beem because polling is the supported integration.

DLR routes are public only for the one-segment callback path. The callback correlates using `providerMessageId`; validate the provider callback payload in staging before relying on it.

## Tenant/customer data API

An administrator can create a `ConnectorConfig` via `/api/connectorConfigs`. It supports `NONE`, `BASIC`, `BEARER`, and `API_KEY` authentication and maps response fields with JSONPath expressions.

- `url` is the remote service base URL.
- `endpoint` is used for a member lookup, replacing `{id}` with the member ID.
- `search` is used for recipient lookup; Flextuma appends caller-supplied query parameters.
- `mappings` maps JSONPath source values to Flextuma keys such as `phoneNumber`, `name`, and template variables.

The search response must be a top-level JSON array. Example mapping:

```json
[
  {"systemKey":"phoneNumber", "jsonPath":"$.phone"},
  {"systemKey":"firstName", "jsonPath":"$.profile.first_name"}
]
```

The trigger endpoint is `POST /api/webhooks/{connector-config-uuid}/sms`; it fetches recipients from the configured tenant API and queues SMS. It requires normal Flextuma authentication. Send either a template code or a raw message body, together with the provider and optional search filter, according to the controller’s `DispatchRequest` contract.

## Implementation gaps and recommendations

### WhatsApp managed onboarding (planned)

The current WhatsApp Cloud API implementation is a bring-your-own-Meta integration. It accepts a customer-owned connector and relays webhooks using a Flextuma-generated callback URL and verification token. It is not a Meta Tech Provider / Embedded Signup integration.

To offer no-secret customer onboarding, Flextuma must first obtain the necessary Meta Tech Provider approvals and advanced permissions. The implementation must then add a server-side Embedded Signup exchange, encrypted tenant-scoped credential storage, WABA and phone-number registration, programmatic webhook subscription, Meta asset-to-tenant routing, token/revocation lifecycle handling, and a customer disconnect path. The browser must never receive persistent Meta credentials or the platform app secret.

Until that work is complete, user-facing copy must state that the customer configures their own Meta app and must paste the generated Flextuma callback URL and verification token into WhatsApp Cloud.

These are code-observed findings as of this repository revision, ordered by impact.

| Priority | Finding | Impact and recommended action |
| --- | --- | --- |
| Resolved | BEEM delivery tracking used a callback-only shape that did not match the documented API. | The adapter stores Beem `request_id` and polls the documented delivery endpoint after five minutes. |
| Resolved | Generic callback IDs did not match Beem delivery identifiers. | The public callback parser accepts Beem `request_id` (and legacy `messageID`) and correlates through `providerMessageId`. |
| Resolved | Generic single-record read, update, and delete skipped the tenant specification. | These operations now use the tenant-scoped specification. Maintain cross-tenant authorization tests as new endpoints are added. |
| Resolved | Raw dispatch used `content` while the queue required `message`. | The trigger now maps its request content to the required queue field. |
| Resolved | PAT authentication ignored a token’s `active` flag. | Inactive tokens are now rejected. |
| Resolved | Tenant API-key authentication sent the wrong stored value. | `API_KEY` connector authentication now sends `apiKey` in `X-API-KEY`. |
| High | API-driven recipient hydration accepts arbitrary stored URLs and caller-controlled query filters without egress controls, timeouts, size limits, or pagination. | Creates SSRF, resource exhaustion, and unintended data-exposure risk. Enforce HTTPS/host allowlists, block private/link-local ranges, set connect/read timeouts and response limits, validate filters, paginate, and audit access. |
| Partially resolved | SMS and campaign workers now use atomic conditional status updates to claim work. | This prevents concurrent replicas from claiming the same PENDING/SCHEDULED row. Add provider idempotency keys and a lease/recovery policy for rows left `PROCESSING` after process failure. |
| High | Campaign dispatch catches errors but can leave campaigns in `PROCESSING`; it also completes after per-recipient debit failures without an explicit partial-failure result. | Operators cannot reliably recover or reconcile campaigns. Model failed/partial states, persist per-recipient outcomes, and alert on stuck campaigns. |
| Medium | Production deployment defaults are unsafe: development Compose, Hibernate `update`, DevTools, mutable bind mounts, `/tmp` uploads, and no health endpoint/migration framework. | Releases are not reproducible or safely observable. Follow [the deployment guide](deployment.md) and add Actuator plus Flyway/Liquibase. |
| Medium | Global CSRF is disabled while cookie sessions are used. | Browser-authenticated write endpoints are exposed to CSRF risk. Enable CSRF protection for session flows, or separate browser/session and token API security models. |
| Medium | Security/operability controls are incomplete: no request timeout for legacy `RestTemplate`, no circuit breaker, no outbound provider rate/concurrency control, no OpenAPI contract, and limited metrics. | Failures are harder to contain, diagnose, and integrate against. Add timeouts, retries with jitter, circuit breaking, metrics/alerts, and a versioned OpenAPI specification. |
| Medium | Sensitive values are stored in database connector records and masked only at JSON serialization. | Database readers/backups may expose third-party credentials. Encrypt at rest with managed keys or reference a secret manager; define rotation and audit procedures. |
| Low | `POST /api/webhooks/{id}/sms` is named as a webhook but is an authenticated dispatch command. | The naming invites unsafe exposure/misconfiguration. Move it under an authenticated integrations/dispatch namespace and document its authorization scope. |

## Minimum acceptance tests

Before enabling any external party in production, automate these tests: valid and invalid PAT authentication; provider credential rejection; one successful send and one provider failure; Beem `request_id` persistence; Beem delivery polling for `PENDING`, `DELIVERED`, and `UNDELIVERED`; malformed/duplicate/out-of-order callbacks; tenant API timeout/5xx/oversize response; recipient pagination; wallet debit/refund reconciliation; and authorization isolation between organisations.
