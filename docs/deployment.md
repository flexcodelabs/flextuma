# Flextuma deployment guide

This guide describes a production deployment of the Flextuma Spring Boot service. Flextuma is stateful only through PostgreSQL and Redis: the application containers can be replicated once those services are shared and durable.

## Production topology

Place a TLS-terminating reverse proxy or load balancer in front of one or more application containers. PostgreSQL must have backups and a tested restore procedure. Redis is required for HTTP sessions and rate-limit state; run it with persistence and high availability appropriate to the availability target.

```
Internet -> TLS proxy / WAF -> Flextuma app replicas -> PostgreSQL
                                      |              -> Redis
                                      -> SMS providers / tenant APIs
```

The proxy must pass `Host`, `X-Forwarded-For`, and `X-Forwarded-Proto`. Make the app reachable only from the proxy, and make PostgreSQL/Redis private to the application network. Permit egress only to approved SMS providers and customer APIs.

## Required configuration

Provide secrets through the platform secret manager, never in the image, repository, or `compose.yaml`. These values are consumed by the current application configuration.

| Setting | Required | Production guidance |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | Yes | PostgreSQL JDBC URL, with TLS when supported by the provider. |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Yes | Dedicated least-privilege database account. |
| `SPRING_DATA_REDIS_HOST` / `SPRING_DATA_REDIS_PORT` | Yes | Shared Redis service; protect with network controls and authentication/TLS where available. |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Yes | Set to `validate`; do not use the repository default `update` in production. |
| `HIKARI_MAX_POOL`, `HIKARI_MIN_IDLE` | Recommended | Size across all replicas below PostgreSQL’s connection limit. |
| `SESSION_TIMEOUT` | Recommended | Session lifetime, e.g. `30m`. |
| `SMS_PRICE_PER_SEGMENT` | Yes | Decimal cost used for wallet accounting; confirm the business unit and currency. |
| `FLEXTUMA_SMS_BEEM_DELIVERY_POLL_INTERVAL_MS` | Optional | Beem delivery-report polling interval in milliseconds; defaults to `60000`. Beem polling starts five minutes after send. |
| `FLEXTUMA_SMS_BEEM_DELIVERY_MINIMUM_DELAY_MINUTES` | Optional | Minimum wait before the first Beem delivery lookup; defaults to `5`, as recommended by Beem. |
| `APP_FRONTEND_DIRECTORY` | If serving UI | Read-only directory containing `index.html` and assets. |
| `APP_UPLOAD_DIRECTORY` | If app uploads are enabled | Durable, access-controlled storage; `/tmp` loses uploads on restart. |
| `LOG_MIN_LEVEL`, `LOG_RETENTION_DAYS` | Recommended | Tune for operating requirements; database log retention has storage impact. |
| `flextuma.auth.*`, `flextuma.verification.*` | Recommended | Explicitly configure authentication attempt, verification, and expiry policy. |

For secure session cookies behind HTTPS, explicitly configure the session cookie domain/path and secure attributes at the deployment layer and test cross-site login behavior. The `AuthCookieProperties` record exists, but the active cookie serializer currently hard-codes an HttpOnly, `SameSite=Lax` cookie and does not apply all of those properties.

## Build and run

Build the immutable production image using the `prod` target:

```bash
docker build --target prod -t registry.example.com/flextuma:VERSION .
docker push registry.example.com/flextuma:VERSION
```

Deploy that image with the required environment above. The committed `compose.yaml` is explicitly a development setup: it selects the `dev` target, enables DevTools/restart behavior, bind-mounts source/classes/client files, uses Hibernate `update`, and depends on an external `local-docker-network`. Do not promote it unchanged.

## Release procedure

1. Run the Gradle test suite and build the WAR/image in CI.
2. Scan the image and dependency tree; record the image digest.
3. Take/verify a PostgreSQL backup and apply versioned database migrations before rollout. The repository currently has no migration tool configured; introduce Flyway or Liquibase before the first managed production release.
4. Deploy one canary replica, validate login, a PAT-authenticated API call, Redis session continuity, a test SMS, and provider callback receipt.
5. Roll out remaining replicas only after the canary passes. Monitor 5xx responses, database connections, Redis availability, pending/failed SMS logs, and callback mismatch warnings.
6. Keep the preceding image digest available for rollback. A rollback must be compatible with the database schema; this is why versioned, backward-compatible migrations are essential.

## Health, backups, and operations

No Actuator health endpoint is currently included. Until one is added, use a platform TCP check plus a protected, lightweight authenticated API check; do not treat `GET /` as an application dependency check. Add Spring Boot Actuator and separate liveness/readiness probes before relying on automated replacement or horizontal scaling.

Back up PostgreSQL (including point-in-time recovery if available), test restores regularly, and monitor storage growth for `smslog`, wallet transactions, and database-backed system logs. Redis loss invalidates active sessions and rate-limit buckets, so document that operational effect and choose persistence accordingly.

Rotate database, Redis, provider, tenant-API, and PAT credentials. Provider connector secrets are stored in the application database; database encryption/backups and access controls therefore fall within the secret-management boundary.

## Pre-launch checklist

- HTTPS, HSTS, proxy headers, WAF/rate limits, and restricted network paths are in place.
- Production configuration does not enable DevTools or Hibernate schema update.
- Database migrations, backup/restore, monitoring, alerting, and on-call ownership are verified.
- Frontend/upload storage is durable where required and has a retention policy.
- SMS sender IDs, provider account limits, wallet price, and DLR callbacks have been tested in the production-like environment.
- The high-priority items in [integration and gap analysis](third-party-integration.md#implementation-gaps-and-recommendations) are resolved or formally accepted.
