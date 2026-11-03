# FTS AI Chatbot Spring Boot Starter

Reusable **Groq → Gemini (HTTP 429 fallback)** chat backend + floating vanilla JS widget for FTS Spring Boot products.

- Artifact: `com.fts:fts-ai-chatbot-spring-boot-starter:0.1.0`
- Endpoint: `POST /api/v1/chat`
- Widget: `/js/portal-chat-widget.js` + `/css/portal-chat-widget.css`

## Consume from GitHub Packages

### 1. Maven `settings.xml` (once per machine / CI)

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_PAT_WITH_read:packages</password>
  </server>
</servers>
```

### 2. Product `pom.xml`

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
  <version>0.1.0</version>
</dependency>
```

### 3. Application config

```yaml
fts:
  ai:
    chat:
      enabled: true
      groq-api-key: ${GROQ_API_KEY}
      groq-model: ${GROQ_CHAT_MODEL:openai/gpt-oss-120b}
      gemini-api-key: ${GEMINI_API_KEY:}
      gemini-model: gemini-2.5-flash
      # optional product-specific system prompt
      # system-prompt: |
      #   You are the WaterFlow assistant...
```

### 4. Layout (Thymeleaf example)

```html
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
<link rel="stylesheet" th:href="@{/css/portal-chat-widget.css}"/>
<!-- ... -->
<script th:src="@{/js/portal-chat-widget.js}" defer></script>
```

Ensure `/api/v1/chat` requires an authenticated session (starter uses `@PreAuthorize("isAuthenticated()")`) and CSRF is enabled for browser POSTs.

### 5. Product tools (optional)

```java
@Component
public class MyProductTools implements FtsAiChatTools {

    @Tool(description = "Explain how billing works")
    public String billingFaq(String query) {
        return "...";
    }
}
```

### 6. Richer user context (optional)

```java
@Bean
ChatUserContextFactory chatUserContextFactory() {
    return auth -> {
        MyUserDetails u = (MyUserDetails) auth.getPrincipal();
        return new ChatUserContext(
                String.valueOf(u.getId()),
                u.getUsername(),
                u.getDisplayName(),
                "...authorities...");
    };
}
```

## Publish a new version

1. Bump `<version>` in `pom.xml`
2. Tag / create a GitHub Release **or** run the **Publish GitHub Package** workflow
3. Consumers bump the dependency version

## Local install (without Packages)

```bash
mvn -DskipTests clean install
```

Then depend on `0.1.0` from the local `.m2` repository.
