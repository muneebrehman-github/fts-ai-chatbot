# Integrating FTS AI Chatbot into a Spring Boot product

This guide explains how to embed the reusable chat **backend + floating widget** from
`com.fts:fts-ai-chatbot-spring-boot-starter` into any FTS Spring Boot application
(Control Center, WaterFlow, Lets Meet, Subscription, etc.).

| Item | Value |
|------|--------|
| Artifact | `com.fts:fts-ai-chatbot-spring-boot-starter` |
| Current version | `0.1.1` |
| Packages URL | `https://maven.pkg.github.com/muneebrehman-github/fts-ai-chatbot` |
| Chat API | `POST /api/v1/chat` |
| Widget assets | `/js/portal-chat-widget.js`, `/css/portal-chat-widget.css` |
| Primary model | Groq (`openai/gpt-oss-120b` by default) |
| Fallback | Gemini (`gemini-2.5-flash`) on HTTP **429** |

Requirements: **Spring Boot 3.4+**, Spring Security (session + CSRF), Spring Web.

---

## 1. GitHub Packages authentication (once)

Create or update `~/.m2/settings.xml`:

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

The `<id>` must be `github` (matches the repository id in `pom.xml`).

**Docker builds:** either pass a token into the image build, or install the starter into
the host `.m2` and copy it into the build context (Control Center uses
`scripts/prepare-docker-m2.ps1` for this).

**Local-only (no Packages):**

```bash
cd fts-ai-chatbot
mvn -DskipTests clean install
```

---

## 2. Add the dependency

In the product `pom.xml`:

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
  <version>0.1.1</version>
</dependency>
```

Auto-configuration loads when `fts.ai.chat.enabled=true` (default).

---

## 3. Application configuration

```yaml
fts:
  ai:
    chat:
      enabled: true
      groq-api-key: ${GROQ_API_KEY}
      groq-model: ${GROQ_CHAT_MODEL:openai/gpt-oss-120b}
      gemini-api-key: ${GEMINI_API_KEY:}
      gemini-model: gemini-2.5-flash
      # Optional product-specific system prompt
      system-prompt: |
        You are the <Product Name> AI assistant.
        Be concise. Use registered tools for factual answers.
```

Environment variables:

```text
GROQ_API_KEY=...
GROQ_CHAT_MODEL=openai/gpt-oss-120b   # optional
GEMINI_API_KEY=...                    # optional 429 fallback
```

---

## 4. Embed the widget (Thymeleaf)

In the shared layout (authenticated pages only):

```html
<head>
  <meta name="_csrf" th:content="${_csrf.token}"/>
  <meta name="_csrf_header" th:content="${_csrf.headerName}"/>
  <link rel="stylesheet" th:href="@{/css/portal-chat-widget.css}"/>
</head>
<body>
  <!-- page content -->
  <script th:src="@{/js/portal-chat-widget.js}" defer></script>
</body>
```

Optional JS override:

```html
<script>
  window.FTS_AI_CHAT = { endpoint: '/api/v1/chat' };
</script>
```

### Security checklist

- Permit static assets if locked down: `/js/**`, `/css/**`
- Keep `POST /api/v1/chat` **authenticated** (starter uses `@PreAuthorize("isAuthenticated()")`)
- Keep **CSRF** enabled for browser POSTs (widget reads `_csrf` meta tags)

### React / SPA hosts

Load the same CSS/JS URLs from the Spring app (or copy them). Ensure every chat
`POST` includes the CSRF header Spring expects (or your SPA’s equivalent).

---

## 5. Product-specific tools (optional)

Implement `FtsAiChatTools` on a `@Component` with Spring AI `@Tool` methods.
The starter registers every such bean with Groq and Gemini clients.

```java
import com.fts.aichat.FtsAiChatTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class MyProductTools implements FtsAiChatTools {

    @Tool(description = "List open shops for the current tenant")
    public String listOpenShops() {
        // Prefer SecurityContextHolder for RBAC inside tools
        return "...";
    }
}
```

Tools run on the request thread, so the logged-in Spring Security context is available.

---

## 6. Richer user context (optional)

Override the default `ChatUserContextFactory` so the model sees your principal’s id / name:

```java
import com.fts.aichat.ChatUserContext;
import com.fts.aichat.ChatUserContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiChatSupportConfig {

    @Bean
    ChatUserContextFactory chatUserContextFactory() {
        return authentication -> {
            MyUserDetails u = (MyUserDetails) authentication.getPrincipal();
            return new ChatUserContext(
                    String.valueOf(u.getId()),
                    u.getUsername(),
                    u.getDisplayName(),
                    "...authorities...");
        };
    }
}
```

---

## 7. Verify

1. Start the app with `GROQ_API_KEY` set
2. Sign in
3. Confirm the floating chat button (bottom-right)
4. Send a message — expect JSON shaped like:

```json
{
  "reply": "...",
  "conversationId": "...",
  "provider": "groq",
  "fallbackUsed": false
}
```

`provider` becomes `"gemini"` and `fallbackUsed: true` after a Groq HTTP 429 when
`GEMINI_API_KEY` is configured.

---

## Checklist

| Step | Required |
|------|----------|
| GitHub Packages auth or local `mvn install` | Yes |
| Starter dependency `0.1.1` | Yes |
| `GROQ_API_KEY` | Yes |
| CSRF meta tags + widget CSS/JS in layout | Yes |
| Authenticated session for `/api/v1/chat` | Yes |
| `FtsAiChatTools` beans | Optional |
| Custom `ChatUserContextFactory` | Optional |
| `GEMINI_API_KEY` | Optional (429 fallback) |
| Custom `fts.ai.chat.system-prompt` | Optional |

---

## Architecture note

Prefer this **starter dependency** in each product (with product-local tools and
SecurityContext). Do **not** route every product’s chat through Control Center
unless you intentionally want a single shared fleet bot with separate service auth.

---

## Publishing a new starter version

1. Bump `<version>` in this repo’s `pom.xml`
2. Create a GitHub Release (or run the **Publish GitHub Package** workflow)
3. Bump the dependency version in each consuming product

See also the root [README.md](../README.md).
