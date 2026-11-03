# FTS AI Chatbot Spring Boot Starter

Reusable **Groq → Gemini (HTTP 429 fallback)** chat backend + floating vanilla JS widget for FTS Spring Boot products.

| | |
|--|--|
| Artifact | `com.fts:fts-ai-chatbot-spring-boot-starter:0.1.1` |
| Endpoint | `POST /api/v1/chat` |
| Widget | `/js/portal-chat-widget.js` + `/css/portal-chat-widget.css` |

**Full integration steps:** [docs/INTEGRATION.md](docs/INTEGRATION.md)

## Quick start

1. Authenticate Maven to GitHub Packages (`settings.xml` server id `github`)
2. Add the dependency
3. Set `GROQ_API_KEY` (and optional `GEMINI_API_KEY`)
4. Include CSRF meta tags + widget CSS/JS in your layout
5. Optionally add `@Component` beans implementing `FtsAiChatTools`

```xml
<dependency>
  <groupId>com.fts</groupId>
  <artifactId>fts-ai-chatbot-spring-boot-starter</artifactId>
  <version>0.1.1</version>
</dependency>
```

```yaml
fts:
  ai:
    chat:
      enabled: true
      groq-api-key: ${GROQ_API_KEY}
      gemini-api-key: ${GEMINI_API_KEY:}
```

## Publish a new version

1. Bump `<version>` in `pom.xml`
2. Create a GitHub Release (triggers **Publish GitHub Package**)
3. Consumers bump the dependency version

## Local install (without Packages)

```bash
mvn -DskipTests clean install
```
