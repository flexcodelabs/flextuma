# WhatsApp Business templates — gap, target design, and implementation guide

**Status: implemented.** The design below shipped as written: [`WhatsAppTemplate`](../src/main/java/com/flexcodelabs/flextuma/core/entities/whatsapp/WhatsAppTemplate.java)
(synced catalogue), [`WhatsAppTemplateSyncService`](../src/main/java/com/flexcodelabs/flextuma/modules/whatsapp/services/WhatsAppTemplateSyncService.java)
(`POST /api/whatsappTemplates/sync`), [`WhatsAppSender.sendTemplate`](../src/main/java/com/flexcodelabs/flextuma/core/senders/WhatsAppSender.java),
[`NotificationService.queueWhatsAppTemplate`](../src/main/java/com/flexcodelabs/flextuma/modules/notification/services/NotificationService.java)
(routed from `POST /api/notifications/whatsapp` when `templateName` is present), and the
`message_template_status_update` handling in
[`WhatsAppWebhookController`](../src/main/java/com/flexcodelabs/flextuma/modules/whatsapp/controllers/WhatsAppWebhookController.java).
The rest of this document is kept as the design record; the "Current state" section below
describes what was true *before* this work landed.

Flextuma's WhatsApp Cloud API integration could previously send only free-form
text messages. It cannot send a **Meta-approved WhatsApp Business template**
message. This is not a minor gap: it blocks every tenant whose WhatsApp use
case is proactive (reminders, alerts, invoices, OTPs) rather than a live
support conversation, because Meta's Cloud API rejects business-initiated
free text outside a narrow window. This document explains the gap, the
target design, and the concrete steps to close it.

## Why this matters

WhatsApp's Cloud API only allows **business-initiated** messages — anything
a tenant's system sends proactively, outside a 24-hour customer-service
window following the recipient's last inbound message — to use a template
Meta has already reviewed and approved. Free-form text sent outside that
window is rejected by Meta (error `131047`, "re-engagement message"). A
template is not just copy formatting: it is a Meta object with a `name`,
`language`, `category`, an approval `status`, and a fixed set of `HEADER` /
`BODY` / `BUTTON` components, each with typed placeholders (`{{1}}`,
`{{2}}`, …) that get filled in per send.

So any tenant whose WhatsApp traffic is scheduler-driven — not a reply to an
inbound chat — needs Flextuma to know how to send `type: "template"`
messages, not just `type: "text"`.

## Current state (as of this revision)

Confirmed by reading the code, not inferred from the README:

- [`WhatsAppSender.sendSms`](../src/main/java/com/flexcodelabs/flextuma/core/senders/WhatsAppSender.java)
  hardcodes `"type": "text"` with `text.body`. There is no `type: "template"`
  branch anywhere in the class.
- [`SmsSender`](../src/main/java/com/flexcodelabs/flextuma/core/services/SmsSender.java),
  the interface every provider (Beem, NextSms, WhatsApp) implements, is
  `sendSms(SmsConnector config, String to, String message)` — a flat string.
  There is no parameter for a template name, language, or structured
  components, for any provider.
- [`NotificationController.sendWhatsApp`](../src/main/java/com/flexcodelabs/flextuma/modules/notification/controllers/NotificationController.java)
  (`POST /api/notifications/whatsapp`) just sets `provider: WHATSAPP` and
  calls the same `queueRawSms` free-text path used for SMS.
- `/api/templates` ([`SmsTemplate`](../src/main/java/com/flexcodelabs/flextuma/core/entities/sms/SmsTemplate.java))
  is Flextuma's own `{{variable}}`-interpolation template system for SMS/text
  copy. It has nothing to do with Meta-approved WhatsApp templates — it
  produces a free-text `content` string, which is exactly what
  `WhatsAppSender` still sends as `type: "text"` even when the provider is
  WhatsApp. There is no entity anywhere that represents a Meta WhatsApp
  Business template (id, category, language, approval status, components).
- The webhook relay
  ([`WhatsAppWebhookController`](../src/main/java/com/flexcodelabs/flextuma/modules/whatsapp/controllers/WhatsAppWebhookController.java))
  already forwards *every* Meta event type verbatim to a tenant's
  `callbackUrl` (see `relay()`), including a `message_template_status_update`
  event — it just isn't specially interpreted here, and nothing on this side
  currently syncs or stores templates to correlate that event against. Note
  it only arrives on the per-tenant generated callback route
  (`POST /api/webhooks/whatsapp/{callbackToken}`), not the phone-number-keyed
  route, because template status events carry no `metadata.phone_number_id`
  for `phoneNumberId(payload)` to match against.
- Neither `ROADMAP/roadmap.md` nor `docs/third-party-integration.md` lists
  WhatsApp template support as planned work today; the only planned
  WhatsApp item is Meta Tech Provider / Embedded Signup onboarding (how a
  tenant *obtains* Meta credentials, unrelated to what gets sent with them).

## Target design

### 1. `WhatsAppTemplate` entity — mirrors Meta's template object

A new entity, synced from Meta's Graph API rather than hand-authored,
following the same `Owner`/`BaseEntity` pattern as every other module:

```java
package com.flexcodelabs.flextuma.core.entities.whatsapp;

@Entity
@Table(name = "whatsapp_template", uniqueConstraints = {
    @UniqueConstraint(name = "unique_meta_template_id", columnNames = { "metaTemplateId", "creator" })
})
public class WhatsAppTemplate extends Owner {
    public static final String PLURAL = "whatsappTemplates";
    public static final String NAME_PLURAL = "WhatsApp Templates";
    public static final String NAME_SINGULAR = "WhatsApp Template";

    private String metaTemplateId;      // Meta's template id
    private String name;                // Template name, e.g. "farm_alert"
    private String category;            // UTILITY | MARKETING | AUTHENTICATION
    private String language;            // e.g. "en", "sw"
    private String status;              // APPROVED | PENDING | REJECTED | PAUSED | DISABLED | ...

    @Column(columnDefinition = "TEXT")
    private String componentsJson;      // Raw components array Meta returned, as JSON text

    @Column(columnDefinition = "TEXT")
    private String placeholdersJson;    // Derived placeholder list (type/format/position), as JSON text

    @ManyToOne(optional = false)
    private SmsConnector connector;     // Which WHATSAPP connector this was synced from

    private LocalDateTime lastSyncedAt;
}
```

Store `componentsJson`/`placeholdersJson` as JSON text columns (matching
`SmsConnector.extraSettings`'s existing `columnDefinition = "TEXT"`
convention) rather than a native `jsonb` type — Flextuma's schema is
Hibernate-managed (`spring.jpa.hibernate.ddl-auto=update`, see
`src/main/resources/application.properties`), and there is no migration
framework yet (tracked as a gap in
[`third-party-integration.md`](third-party-integration.md)), so keep new
columns to types Hibernate can create unattended. A template Meta no longer
returns should be marked `status: "REMOVED"` on the next sync rather than
deleted, so any existing send configuration referencing it doesn't dangle —
this mirrors how flexfarm-core's own `whatsapp-template.entity.ts` already
handles the same case.

Wire it up with the same three-file pattern every other module uses —
compare directly against `SmsConnector`'s equivalents:

- `WhatsAppTemplateRepository extends JpaRepository<WhatsAppTemplate, UUID>, JpaSpecificationExecutor<...>`
- `WhatsAppTemplateService extends BaseService<WhatsAppTemplate>` (see
  [`SmsConnectorService`](../src/main/java/com/flexcodelabs/flextuma/modules/sms/services/SmsConnectorService.java)
  for the exact override shape: `getRepository`, `getReadPermission` /
  `getAddPermission` / `getUpdatePermission` / `getDeletePermission`,
  `getEntityPlural`, `getEntitySingular`, `getPropertyName`,
  `getRepositoryAsExecutor`, `getTableName`)
- `WhatsAppTemplateController extends BaseController<WhatsAppTemplate, WhatsAppTemplateService>`
  mounted at `/api/` + `WhatsAppTemplate.PLURAL`

This gives read-only CRUD browsing (`GET /api/whatsappTemplates`,
`GET /api/whatsappTemplates/{id}`) for free, matching the shared-CRUD
conventions already documented in the main [README](../README.md#shared-crud-endpoints).
Block `POST`/`PUT`/`DELETE` at the service layer (return `403` or simply
omit `ADD`/`UPDATE`/`DELETE` permissions) — templates are synced, not
hand-authored, exactly like `whatsapp-template.entity.ts` already enforces
on the flexfarm-core side.

### 2. Template sync

Add `WhatsAppTemplateSyncService.sync(SmsConnector connector)`:

1. `GET https://graph.facebook.com/{version}/{wabaId}/message_templates?limit=100`
   using the connector's `key` as a Bearer token, following
   `paging.next` to exhaustion. The WABA id isn't currently a `SmsConnector`
   field — add it to `extraSettings` (e.g. `{"businessAccountId":"..."}`) or
   as a new `businessAccountId` column; either fits the entity's existing
   free-form-JSON escape hatch or its column-per-provider-setting pattern.
2. Upsert each returned template by `(metaTemplateId, connector.createdBy)`,
   deriving `placeholdersJson` from each component's `{{n}}` count/format —
   port the logic flexfarm-core already wrote for this in
   `whatsapp-template.service.ts` rather than re-deriving it from scratch.
3. Mark any previously-synced template no longer returned as
   `status = "REMOVED"`.

Expose it as `POST /api/whatsappTemplates/sync` (tenant-scoped: only syncs
templates for the caller's own connector(s)), mirroring flexfarm-core's own
`POST /api/whatsappTemplates/sync` endpoint one-for-one.

### 3. Sending a template message

Add a template-send path alongside the existing free-text path, without
changing existing behavior:

**Sender**, extend `WhatsAppSender` with a second method (the shared
`SmsSender` interface stays as-is for the free-text path every provider
still uses; this is WhatsApp-specific, so it doesn't need to live on the
interface):

```java
public SmsSendResult sendTemplate(SmsConnector config, String to, String templateName,
        String languageCode, List<Map<String, Object>> components) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("messaging_product", "whatsapp");
    body.put("to", normaliseRecipient(to));
    body.put("type", "template");
    body.put("template", Map.of(
        "name", templateName,
        "language", Map.of("code", languageCode),
        "components", components));
    // ... same RestTemplate POST + SmsSendResult mapping as sendSms()
}
```

This is exactly the shape flexfarm-core's own Meta client already builds in
[`notification-whatsapp.service.ts`](../../../flexfarm/flexfarm-core/src/notification/services/notification-whatsapp.service.ts)
(`sendTemplateMessage`) — reuse that as the reference for the `components`
array's per-type structure (`header`/`body` text parameters, `header` media
parameters as `{type: "image", image: {link: url}}`, and `button` dynamic-URL
parameters keyed by `sub_type: "url"` + `index`).

**API surface**: extend `NotificationController.sendWhatsApp` to branch on
whether the request carries template fields:

```json
POST /api/notifications/whatsapp
{
  "phoneNumber": "+255700000000",
  "templateName": "farm_alert",
  "templateLanguage": "en",
  "components": [
    { "type": "body", "parameters": [{ "type": "text", "text": "Feed is running low" }] }
  ]
}
```

When `templateName` is present, `NotificationService` should route to
`WhatsAppTemplateSendService` (new, mirrors `queueRawSms`'s shape: resolve
the connector, validate rate limit, save an `SmsLog` with `content` set to a
human-readable rendering of the template send for the dashboard, and call
`WhatsAppSender.sendTemplate` instead of `sendSms`). When `templateName` is
absent, behavior is byte-for-byte unchanged — existing integrations keep
working.

Do not require the caller to look up `WhatsAppTemplate` rows itself for the
component values — callers (like flexfarm-core) already own "which template
for which event, filled with which values" in their own domain; Flextuma's
job is only to relay a pre-built `components` array to Meta and track the
resulting `SmsLog`. Keep the template *catalogue* (`WhatsAppTemplate`,
step 1) for validation/browsing (e.g. reject a send whose `templateName` +
`templateLanguage` doesn't match a `status: "APPROVED"` synced row, the way
flexfarm-core already refuses to send through an unapproved assignment) —
but don't force callers through a second admin UI to configure a
`variableMappings`-style indirection Flextuma doesn't need to own.

### 4. Template status stays in sync

No new work needed on the receiving side: the webhook relay already
forwards `message_template_status_update` events verbatim to a tenant's
`callbackUrl` (`WhatsAppWebhookController.relay()`). Add one thing:
special-case that event type in `WhatsAppWebhookController.handle()` to also
update the matching local `WhatsAppTemplate.status` (by `metaTemplateId`,
falling back to name+language) before relaying, the same way
flexfarm-core's own `AppController` → `WhatsappTemplateService.applyStatusUpdate`
already does — so an approval/rejection is reflected in
`GET /api/whatsappTemplates` immediately instead of waiting for the next
manual sync.

## Implementation guide (ordered)

1. **Entity + repository** — add `WhatsAppTemplate` under
   `core/entities/whatsapp/`, `WhatsAppTemplateRepository` under
   `core/repositories/`. Follow `SmsConnector`/`SmsConnectorRepository`
   exactly for annotations and conventions.
2. **Service + controller** — add `WhatsAppTemplateService extends BaseService<WhatsAppTemplate>`
   and `WhatsAppTemplateController extends BaseController<...>` under
   `modules/whatsapp/services/` and `modules/whatsapp/controllers/`,
   read-only permissions only.
3. **Sync** — add `WhatsAppTemplateSyncService` with the Graph API fetch +
   upsert logic from "Template sync" above, and wire
   `POST /api/whatsappTemplates/sync` into `WhatsAppTemplateController`.
   Add `businessAccountId` (or an `extraSettings` key) to `SmsConnector` if a
   dedicated column is preferred over the JSON escape hatch.
4. **Sender** — add `WhatsAppSender.sendTemplate(...)` per "Sending a
   template message" above. Unit test it the same way
   `WhatsAppSenderTest` (if one exists) covers `sendSms` today — assert the
   POST body shape, not just that a call was made.
5. **Send path** — add `WhatsAppTemplateSendService` (or extend
   `NotificationService`) to branch `POST /api/notifications/whatsapp` on
   `templateName` presence, validate the named template is
   `status: "APPROVED"` for the caller's connector, build `components` from
   the request, and call the new sender method. Reuse `SmsLog` for tracking
   — no new log table needed.
6. **Webhook status sync** — extend `WhatsAppWebhookController.handle()` to
   update the matching `WhatsAppTemplate.status` when the payload's
   `changes[].field` is `message_template_status_update`, before the
   existing `relay()` call runs.
7. **Tests** — cover: sync upserts a new template and marks a
   Meta-removed one `REMOVED`; send is rejected when no `APPROVED` template
   matches `(templateName, templateLanguage, connector)`; send builds the
   exact Meta payload shape for text/media header, body, and dynamic-URL
   button components; a `message_template_status_update` webhook event
   updates the local row *and* still relays to the tenant's `callbackUrl`;
   existing free-text `POST /api/notifications/whatsapp` behavior is
   unchanged when no `templateName` is supplied.
8. **Docs** — update this file's "Current state" section once shipped, and
   add a row to `docs/third-party-integration.md`'s gap table marking this
   "Resolved" (see the existing rows there for the format).

## What does *not* need to change

- The `SmsSender` interface, `BeemSender`, and `NextSmsSender` are
  untouched — the new method is WhatsApp-specific and additive.
- Existing free-text `POST /api/notifications/whatsapp` callers are
  unaffected; the template path only activates when `templateName` is
  present in the request body.
- Connector setup (`POST /api/connectors` with `provider: "WHATSAPP"`),
  billing (system-connector-only wallet debit), rate limiting (10/sec/tenant
  via `RateLimiterService`), and the `WHATSAPP_SEND` feature gate all
  already work as-is and need no changes for template sends.

## Minimum acceptance tests

Following the same convention as
[`third-party-integration.md`](third-party-integration.md#minimum-acceptance-tests):
sync creates/updates/removes templates correctly against a mocked Graph API
response; a send with a non-existent or non-approved `templateName` is
rejected before any Meta call; a send with a valid template produces the
exact `type: "template"` payload shape for each component kind (text
header, media header, body, dynamic-URL button); a duplicate or malformed
`message_template_status_update` webhook doesn't corrupt template state;
relay to the tenant `callbackUrl` still fires for that event type; and the
existing free-text send path has zero behavior change under this feature's
test suite.
