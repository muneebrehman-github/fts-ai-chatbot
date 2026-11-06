# FTS AI Chatbot � Integration User Guide

Step-by-step guide to add the FTS AI chatbot to a website or a Spring Boot product.

| | Public website embed | Spring Boot product |
|--|--|--|
| **Use when** | Marketing site, WordPress, React/Next, Lovable, plain HTML | Authenticated FTS portals (Control Center, WaterFlow, �) |
| **How it works** | Hosted `chat-api` + script tag | Maven starter inside your app |
| **Auth** | `siteKey` (+ Origin allowlist) | Logged-in session + CSRF |
| **Detail docs** | [EMBED.md](EMBED.md) | [INTEGRATION.md](INTEGRATION.md) |

**Version:** `0.2.1` � Primary model: Groq � Fallback: Gemini on HTTP **429**

---

## Choose your path

```
Do you have a Spring Boot app with Spring Security sessions?
        �
   Yes -+-? Path B � Spring Boot starter (authenticated chat + tools)
        �
   No  ---? Path A � Public embed (any website)
```

Most marketing / external sites use **Path A**. Internal FTS portals use **Path B**.

---

## Path A � Integrate on any website

### A1. Prerequisites

- A running **chat-api** instance (local, Docker, or VM)
- `GROQ_API_KEY` on the **server** (never in frontend env)
- Optional: `GEMINI_API_KEY` for 429 fallback
- Your site�s public origin, e.g. `https://www.example.com`

### A2. Get chat-api running

**Local:**

```bash
export GROQ_API_KEY=...
export GEMINI_API_KEY=...   # optional
mvn -pl chat-api -am -DskipTests package
java -jar chat-api/target/fts-ai-chat-api-0.2.1.jar
```

Default local port: **8088**. Demo page: http://localhost:8088/embed-demo.html (`siteKey`: `demo-site`).

**Docker / VM:** see [CICD_GITHUB_DOCKER_VM.md](CICD_GITHUB_DOCKER_VM.md) and `deploy/docker-compose.yml`.  
Compose maps host `${HOST_PORT:-8080}` → container `8080`. Put secrets in the VM `.env`:

```env
GROQ_API_KEY=...
GEMINI_API_KEY=...
PORT=8080
HOST_PORT=8084
```

Confirm health:

```bash
curl https://CHAT_HOST/health
```

Expected shape:

```json
{ "status": "ok", "chatEnabled": true, "groqConfigured": true, "geminiConfigured": false }
```

### A3. Register your site

Add a site entry under `fts.ai.public.sites` (in `application.yml` or external config mounted on the host):

```yaml
fts:
  ai:
    public:
      sites:
        marketing:
          site-key: mkt-live-key          # public embed token � rotate if leaked
          enabled: true                   # set false to cut off this project immediately
          expires-at: 2027-12-31T23:59:59Z  # optional ISO-8601; omit = never expires
          allowed-origins:
            - https://www.example.com
            - http://localhost:3000       # local frontend during development
          title: Example Assistant
          subtitle: We are here to help
          welcome: Hi � ask about our product.
          placeholder: Type a message�
          launcher-label: Chat with us
          primary-color: "#2fe0c4"
          system-prompt: |
            You are the Example.com website assistant.
            Be concise and friendly.
          rate-limit-per-minute: 120
          rate-limit-per-ip-per-minute: 40
```

| Field | Required | Notes |
|-------|----------|--------|
| `site-key` | Yes | Widget sends this; treat like an embed token |
| `enabled` | No | Default `true`. Set `false` to disable without deleting the entry |
| `expires-at` | No | ISO-8601 instant (e.g. `2027-12-31T23:59:59Z`). After expiry returns 403. Omit = never |
| `allowed-origins` | Prod: yes | Empty = allow any origin (**dev only**) |
| Branding fields | No | Title, colors, welcome text, etc. |
| `system-prompt` | No | Per-site instructions for the model |
| Rate limits | No | In-memory, per JVM instance |

Restart chat-api after config changes.

### A4. Drop the embed snippet

Replace `CHAT_HOST` and the site key with your values:

```html
<link rel="stylesheet" href="https://CHAT_HOST/widget.css" />
<script
  src="https://CHAT_HOST/widget.js"
  data-site-key="mkt-live-key"
  data-api-base="https://CHAT_HOST"
  defer
></script>
```

Place the `<link>` in `<head>` (or before `</body>`). Place the `<script>` once per page, ideally before `</body>`.

#### React / Next.js

```jsx
import { useEffect } from 'react';

const CHAT_HOST = 'https://CHAT_HOST';
const SITE_KEY = 'mkt-live-key';

export function FtsChatWidget() {
  useEffect(() => {
    if (document.getElementById('fts-chat-css')) return;

    const link = document.createElement('link');
    link.id = 'fts-chat-css';
    link.rel = 'stylesheet';
    link.href = `${CHAT_HOST}/widget.css`;
    document.head.appendChild(link);

    const script = document.createElement('script');
    script.src = `${CHAT_HOST}/widget.js`;
    script.defer = true;
    script.dataset.siteKey = SITE_KEY;
    script.dataset.apiBase = CHAT_HOST;
    document.body.appendChild(script);

    return () => {
      // Optional: leave assets mounted across SPA navigations
    };
  }, []);

  return null;
}
```

Or set config before the script loads:

```html
<script>
  window.FTS_PUBLIC_CHAT = {
    siteKey: 'mkt-live-key',
    apiBase: 'https://CHAT_HOST',
    title: 'Example Assistant'
  };
</script>
<script src="https://CHAT_HOST/widget.js" defer></script>
<link rel="stylesheet" href="https://CHAT_HOST/widget.css" />
```

#### WordPress

Appearance ? Theme File Editor (or a �Header/Footer scripts� plugin): paste the HTML snippet in the footer so it loads on every page. Ensure your WordPress origin is in `allowed-origins`.

#### Optional `data-*` overrides

| Attribute | Purpose |
|-----------|---------|
| `data-title` | Panel title |
| `data-subtitle` | Panel subtitle |
| `data-welcome` | First assistant bubble |
| `data-placeholder` | Input placeholder |
| `data-launcher-label` | Floating button label / aria |
| `data-primary-color` | Accent color (e.g. `#2fe0c4`) |
| `data-storage-key` | `sessionStorage` key for conversation id |

Server branding from `GET /api/v1/public/config?siteKey=...` wins when available; attributes are fallbacks.

### A5. Verify Path A

1. Open your site in the browser
2. Confirm a floating launcher (bottom-right)
3. Send a test message
4. In DevTools ? Network, `POST .../api/v1/public/chat` should return:

```json
{
  "reply": "...",
  "conversationId": "...",
  "provider": "groq",
  "fallbackUsed": false
}
```

---

## Path B � Integrate in a Spring Boot product

### B1. Prerequisites

- Spring Boot **3.4+**, Spring Web, Spring Security (session + CSRF)
- Maven access to GitHub Packages **or** a local `mvn install` of this repo
- `GROQ_API_KEY` available to the app process

### B2. Authenticate Maven (once)

`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_PAT_WITH_read:packages</password>
    </server>
  </servers>
</settings>
```

`<id>` must be `github`.

**Without Packages** (local install of this repo):

```bash
cd fts-ai-chatbot
mvn -DskipTests clean install
```

### B3. Add the dependency

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/muneebrehman-github/fts-ai-chatbot</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.fts</groupId>
  <artifactId>fts-ai-chatbot-spring-boot-starter</artifactId>
  <version>0.2.1</version>
</dependency>
```

### B4. Configure the app

```yaml
fts:
  ai:
    chat:
      enabled: true
      groq-api-key: ${GROQ_API_KEY}
      groq-model: ${GROQ_CHAT_MODEL:openai/gpt-oss-120b}
      gemini-api-key: ${GEMINI_API_KEY:}
      gemini-model: gemini-2.5-flash
      system-prompt: |
        You are the <Product Name> AI assistant.
        Be concise. Use registered tools for factual answers.
```

```text
GROQ_API_KEY=...
GEMINI_API_KEY=...          # optional
GROQ_CHAT_MODEL=openai/gpt-oss-120b   # optional
```

### B5. Embed the portal widget (Thymeleaf)

On authenticated layouts only:

```html
<head>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
  <link rel="stylesheet" th:href="@{/css/portal-chat-widget.css}"/>
</head>
<body>
  <!-- page content -->
  <script>
    window.FTS_AI_CHAT = {
      endpoint: '/api/v1/chat',
      storageKey: 'product-ai-chat-conversation-id',
      title: 'AI Assistant',
      subtitle: 'Product help � tools-aware',
      welcome: 'Hi � ask how to use this portal.',
      placeholder: 'Ask a question�',
      launcherLabel: 'AI assistant'
    };
  </script>
  <script th:src="@{/js/portal-chat-widget.js}" defer></script>
</body>
```

Security:

- Permit static assets if locked down: `/js/**`, `/css/**`
- Keep `POST /api/v1/chat` **authenticated**
- Keep CSRF enabled (widget reads the meta tags)

### B6. Optional � product tools

```java
import com.fts.aichat.FtsAiChatTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class MyProductTools implements FtsAiChatTools {

    @Tool(description = "List open shops for the current tenant")
    public String listOpenShops() {
        return "...";
    }
}
```

Tools run on the request thread, so `SecurityContextHolder` is available for RBAC.

### B7. Optional � richer user context

Provide a `ChatUserContextFactory` bean so the model sees your principal�s id / name / authorities. Full example: [INTEGRATION.md](INTEGRATION.md#6-richer-user-context-optional).

### B8. Verify Path B

1. Start the app with `GROQ_API_KEY`
2. Sign in
3. Open an authenticated page � floating button bottom-right
4. Send a message � expect `POST /api/v1/chat` JSON with `reply`, `conversationId`, `provider`

---

## Customization cheat sheet

| Goal | Public embed | Spring starter |
|------|--------------|----------------|
| Change title / welcome | Site YAML or `data-*` / `FTS_PUBLIC_CHAT` | `window.FTS_AI_CHAT` |
| Accent color | `primary-color` / `data-primary-color` | CSS variables in `portal-chat-widget.css` |
| Model instructions | Per-site `system-prompt` | `fts.ai.chat.system-prompt` |
| Call product APIs | Not supported in public API | Implement `FtsAiChatTools` |
| Conversation memory | Browser `sessionStorage` id | Browser `sessionStorage` id |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| No floating button | Script/CSS URL wrong, or missing `data-site-key` | Check console for `[FTS AI Chat]` warnings; confirm network 200 for `widget.js` / `widget.css` |
| CORS / blocked request | Origin not allowlisted | Add exact origin (scheme + host + port) to `allowed-origins` |
| `401` / `403` on Spring chat | Not logged in, or CSRF missing | Ensure session cookie + `_csrf` meta tags |
| `chatEnabled: false` or empty replies | Missing `GROQ_API_KEY` | Set env on the **server** / container, restart |
| Always `provider: gemini` | Groq 429 or misconfigured Groq | Check Groq key/quota; Gemini is intentional fallback |
| Rate limit errors | Too many requests | Raise limits or put CDN/WAF in front of multi-instance fleets |
| Widget on wrong host | `data-api-base` points elsewhere | Set `data-api-base` to the chat-api public URL |

---

## Quick checklist

### Public website

- [ ] chat-api deployed and `/health` OK
- [ ] Site entry with `site-key` + production `allowed-origins` (+ optional `enabled` / `expires-at`)
- [ ] Snippet on the page (`widget.css` + `widget.js`)
- [ ] Keys only on server � not in frontend env
- [ ] Test message returns a reply

### Spring Boot product

- [ ] Starter `0.2.1` on classpath (Packages or local install)
- [ ] `GROQ_API_KEY` set
- [ ] CSRF meta + widget CSS/JS on authenticated layout
- [ ] `/api/v1/chat` requires authentication
- [ ] Optional tools / context factory if needed

---

## Related docs

| Doc | Audience |
|-----|----------|
| [EMBED.md](EMBED.md) | Public API contract & security notes |
| [INTEGRATION.md](INTEGRATION.md) | Deep Spring Boot / tools / publishing |
| [CICD_GITHUB_DOCKER_VM.md](CICD_GITHUB_DOCKER_VM.md) | Deploy chat-api via GitHub ? Docker Hub ? VM |
| [README.md](../README.md) | Repo overview & quick starts |
