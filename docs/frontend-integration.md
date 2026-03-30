# Frontend Integration Guide

This guide describes how to integrate the Flextuma API into a frontend application (e.g., React, Vue, or Next.js).

## 1. Authentication Strategy

### For Internal Dashboards (Session-based)
Internal tools should use the standard login process, which sets a secure `SESSION` cookie.
- **Login**: `POST /api/login`
- Following requests will automatically include the cookie via `credentials: 'include'`.

### For External Apps / Programmatic Access (PAT-based)
External services should use Personal Access Tokens in the header.
- **Header**: `X-API-KEY: <your_raw_token>`
- **Security**: Never expose your PAT in client-side code that is public. Use it only in secure, server-side environments or behind a proxy.

## 2. Common Patterns

### Handling SMS Scheduling
Users can schedule messages by passing a `scheduledAt` field in ISO-8601 format:
```javascript
const payload = {
  phoneNumber: "255...",
  templateCode: "...",
  scheduledAt: new Date(Date.now() + 3600000).toISOString() // 1 hour from now
};
```

### Real-time Preview
When building a template editor, use the `/api/smsTemplates/preview` endpoint for live character counting and segment calculation.
- Useful for showing users how much a message will cost.
- Avoids server-side "surprises" on message length.

## 3. Error Handling
The API uses standard HTTP status codes:
- `400 Bad Request`: Missing variables, invalid data.
- `401 Unauthorized`: Invalid PAT or session.
- `403 Forbidden`: Insufficient permissions.
- `429 Too Many Requests`: Rate limit exceeded (Bucket4j).

## 4. Dashboard Integration

The dashboard can now use user-scoped summary data directly from the backend. All data below is filtered to the currently authenticated user.

### Summary Endpoint

- `GET /api/dashboard/summary`

Example response:

```json
{
  "userId": "6269df23-f8a0-4776-bd89-3015521bc19d",
  "username": "admin",
  "sent": 2847,
  "failed": 23,
  "balanceAmount": 50000,
  "balance": "TZS 50000",
  "currency": "TZS",
  "activeCampaigns": 3,
  "today": 156,
  "thisWeek": 892,
  "thisMonth": 2847,
  "successRate": 99.2,
  "statusBreakdown": {
    "sent": 84.5,
    "failed": 0.7,
    "pending": 10.3,
    "other": 4.5
  }
}
```

How to use it:
- KPI cards: `sent`, `failed`, `balance`, `activeCampaigns`
- Time cards: `today`, `thisWeek`, `thisMonth`
- Success widget: `successRate`
- Doughnut or stacked chart: `statusBreakdown`

Example fetch:

```javascript
const response = await fetch('/api/dashboard/summary', {
  method: 'GET',
  credentials: 'include'
});

if (!response.ok) {
  throw new Error('Failed to load dashboard summary');
}

const summary = await response.json();
```

### Notifications Feed

- `GET /api/notifications?page=1&pageSize=15`

This endpoint now follows the project pagination style instead of using a custom limit parameter.

Example response:

```json
{
  "page": 1,
  "total": 42,
  "pageSize": 15,
  "data": [
    {
      "id": "2f7fcb14-f99d-4983-9a27-104e55f96eb1",
      "phoneNumber": "+255700000000",
      "message": "Your OTP is 1234",
      "status": "delivered",
      "provider": "BEEM",
      "createdAt": "2026-03-29T12:00:00",
      "updatedAt": "2026-03-29T12:00:10"
    }
  ]
}
```

Recommended usage:
- Recent activity list: render `data`
- Pager or infinite scroll: use `page`, `pageSize`, and `total`
- Status pill: map `sent`, `delivered`, `failed`, `pending`, `processing`

Example fetch:

```javascript
async function loadNotifications(page = 1, pageSize = 15) {
  const response = await fetch(
    `/api/notifications?page=${page}&pageSize=${pageSize}`,
    { credentials: 'include' }
  );

  if (!response.ok) {
    throw new Error('Failed to load notifications');
  }

  return response.json();
}
```

### Existing Endpoints Still Available

The dashboard can still consume the existing raw resources when needed:
- `GET /api/wallets`
- `GET /api/campaigns`
- `GET /api/smsLogs`
- `POST /api/logout`

Recommended pattern:
- Use `/api/dashboard/summary` for top-level dashboard widgets
- Use `/api/notifications` for recent message activity
- Use the existing resource endpoints for full management pages and drill-down tables

### SPA Routing

The backend now serves the frontend `index.html` for any non-API route.

Examples:
- `/dashboard`
- `/dashboard/campaigns`
- `/settings/profile`

These routes will return the client app entry file, while `/api/**` remains reserved for backend APIs.

## 5. Personal Notifications Integration

The app also supports user-facing personal notifications for dropdowns, badges, and a full notification center.

### Personal Notifications List

- `GET /api/personalNotifications?page=1&pageSize=10`

Response shape:

```json
{
  "page": 1,
  "total": 12,
  "pageSize": 10,
  "personalNotifications": [
    {
      "id": "0dbe588d-7b7e-4bb9-a6e9-9fa1228a19f4",
      "title": "Low Balance Alert",
      "message": "Your wallet balance is below TZS 10,000. Current balance: TZS 9500.",
      "type": "LOW_BALANCE_ALERT",
      "linkUrl": "/finance/wallet",
      "readAt": null,
      "created": "2026-03-30T16:20:00",
      "updated": "2026-03-30T16:20:00"
    }
  ]
}
```

Use this for:
- full “View All Notifications” page
- paginated notification center
- notification history screens

### Personal Notifications Summary

- `GET /api/personalNotifications/summary?pageSize=5`

Use this for the header bell dropdown like the one in your screenshot.

```json
{
  "unreadCount": 3,
  "notifications": [
    {
      "id": "0dbe588d-7b7e-4bb9-a6e9-9fa1228a19f4",
      "title": "Low Balance Alert",
      "message": "Your wallet balance is below TZS 10,000. Current balance: TZS 9500.",
      "type": "LOW_BALANCE_ALERT",
      "linkUrl": "/finance/wallet",
      "readAt": null,
      "created": "2026-03-30T16:20:00",
      "updated": "2026-03-30T16:20:00"
    },
    {
      "id": "1f0e8a6a-1d43-48f4-a0ae-0f1b6de8697b",
      "title": "Campaign Completed",
      "message": "Summer Sale 2024 has finished sending.",
      "type": "CAMPAIGN_COMPLETED",
      "linkUrl": "/campaigns",
      "readAt": null,
      "created": "2026-03-30T15:55:00",
      "updated": "2026-03-30T15:55:00"
    }
  ]
}
```

### Mark One Notification as Read

- `POST /api/personalNotifications/{id}/read`

Recommended when:
- user clicks a notification item
- user opens a notification detail
- user navigates through a notification deep link

### Mark All Notifications as Read

- `POST /api/personalNotifications/readAll`

Example response:

```json
{
  "message": "Notifications marked as read",
  "updated": 3
}
```

### Example Frontend Flow

```javascript
async function loadPersonalNotificationSummary(pageSize = 5) {
  const response = await fetch(
    `/api/personalNotifications/summary?pageSize=${pageSize}`,
    { credentials: 'include' }
  );

  if (!response.ok) {
    throw new Error('Failed to load personal notifications');
  }

  return response.json();
}

async function markNotificationRead(id) {
  const response = await fetch(`/api/personalNotifications/${id}/read`, {
    method: 'POST',
    credentials: 'include'
  });

  if (!response.ok) {
    throw new Error('Failed to mark notification as read');
  }

  return response.json();
}
```

Recommended UI behavior:
- use `unreadCount` for the blue badge count
- render `notifications` in the dropdown panel
- show relative time from `created`
- navigate to `linkUrl` when present
- for “View All Notifications”, call `GET /api/personalNotifications?page=1&pageSize=10`

### Current Automatic Notification Types

These are generated from real backend events:
- `LOW_BALANCE_ALERT`
- `CAMPAIGN_COMPLETED`
