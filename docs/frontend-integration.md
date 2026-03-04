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
