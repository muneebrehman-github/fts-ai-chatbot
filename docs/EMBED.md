# Embedding FTS AI Chat on any website

Host the **chat-api** service once, then drop a script tag on React, Next.js, WordPress,
Lovable, plain HTML, or any other stack. No Spring session or CSRF required.

| Item | Value |
|------|--------|
| Chat API | `POST /api/v1/public/chat` |
| Widget config | `GET /api/v1/public/config?siteKey=...` |
| Health | `GET /health` |
| Widget assets | `/widget.js`, `/widget.css`, `/embed-demo.html` |
| Auth | `siteKey` (+ optional `Origin` allowlist) |
| Primary model | Groq (`openai/gpt-oss-120b` by default) |
| Fallback | Gemini on HTTP **429** |

API keys (`GROQ_API_KEY`, `GEMINI_API_KEY`) stay on the server only.

---

## 1. Run / deploy chat-api

```bash
export GROQ_API_KEY=...
export GEMINI_API_KEY=...   # optional
mvn -pl chat-api -am -DskipTests package
java -jar chat-api/target/fts-ai-chat-api-0.2.0.jar
```

Or after `mvn -DskipTests install`: `mvn -f chat-api/pom.xml spring-boot:run`

Default port: **8088** (`PORT` env overrides).

---

## 2. Configure sites

In `chat-api` `application.yml` (or externalized config):

```yaml
fts:
  ai:
    chat:
      groq-api-key: ${GROQ_API_KEY}
      gemini-api-key: ${GEMINI_API_KEY:}
    public:
      sites:
        marketing:
          site-key: mkt-live-key
          allowed-origins:
            - https://www.example.com
            - http://localhost:3000
          title: Example Assistant
          subtitle: We are here to help
          welcome: Hi — ask about our product.
          placeholder: Type a message…
          launcher-label: Chat with us
          primary-color: "#2fe0c4"
          system-prompt: |
            You are the Example.com website assistant.
            Be concise and friendly.
          rate-limit-per-minute: 120
          rate-limit-per-ip-per-minute: 40
```

| Field | Notes |
|-------|--------|
| `site-key` | Public id the widget sends (treat like an embed token; rotate if leaked) |
| `allowed-origins` | CORS + request Origin check. Empty list = allow any origin (dev only) |
| `system-prompt` | Per-site instructions (falls back to global `fts.ai.chat.system-prompt`) |
| rate limits | Sliding 1-minute windows, in-memory (per instance) |

---

## 3. Embed snippet

```html
<link rel="stylesheet" href="https://CHAT_HOST/widget.css" />
<script
  src="https://CHAT_HOST/widget.js"
  data-site-key="mkt-live-key"
  data-api-base="https://CHAT_HOST"
  defer
></script>
```

Optional `data-*` overrides: `data-title`, `data-subtitle`, `data-welcome`,
`data-placeholder`, `data-launcher-label`, `data-primary-color`, `data-storage-key`.

Or set `window.FTS_PUBLIC_CHAT = { siteKey, apiBase, title, ... }` before the script.

The widget fetches branding from `/api/v1/public/config` when available.

---

## 4. API contract

### `POST /api/v1/public/chat`

```json
{
  "message": "Hello",
  "conversationId": "optional-uuid",
  "siteKey": "mkt-live-key"
}
```

```json
{
  "reply": "...",
  "conversationId": "uuid",
  "provider": "groq",
  "fallbackUsed": false
}
```

### `GET /api/v1/public/config?siteKey=mkt-live-key`

Returns title / subtitle / welcome / placeholder / launcherLabel / primaryColor (no secrets).

### `GET /health`

```json
{ "status": "ok", "chatEnabled": true, "groqConfigured": true, "geminiConfigured": false }
```

---

## Security notes

- Do **not** put Groq/Gemini keys in frontend env vars
- Restrict `allowed-origins` in production
- Rotate `siteKey` if it is abused
- Rate limits are per JVM; put a CDN/WAF in front for multi-instance fleets

---

## Local demo

With chat-api running:

1. Open http://localhost:8088/embed-demo.html
2. Use site key `demo-site` (empty allowlist for local convenience)
