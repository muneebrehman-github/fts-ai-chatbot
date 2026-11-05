# FTS AI Chatbot

Reusable **Groq → Gemini (HTTP 429 fallback)** chat for FTS products and any website.

| Module | Artifact / app | Audience |
|--|--|--|
| `core` | `com.fts:fts-ai-chatbot-core:0.2.0` | Shared LLM clients + fallback |
| `starter` | `com.fts:fts-ai-chatbot-spring-boot-starter:0.2.0` | Java / Spring Boot products (session + CSRF) |
| `chat-api` | runnable Spring Boot app | Any website via public embed API + widget |

**Integration user guide (start here):** [docs/USER_GUIDE.md](docs/USER_GUIDE.md)  
**Spring Boot (detail):** [docs/INTEGRATION.md](docs/INTEGRATION.md)  
**Public embed (detail):** [docs/EMBED.md](docs/EMBED.md)  
**Deploy chat-api (CI/CD):** [docs/CICD_GITHUB_DOCKER_VM.md](docs/CICD_GITHUB_DOCKER_VM.md)

## Quick start — any website

1. Run the hosted chat API (`chat-api`) with `GROQ_API_KEY` (optional `GEMINI_API_KEY`)
2. Configure a `siteKey` + allowed origins in `application.yml`
3. Drop this snippet on any page (React, Next, WordPress, Lovable, plain HTML, …):

```html
<link rel="stylesheet" href="https://CHAT_HOST/widget.css" />
<script
  src="https://CHAT_HOST/widget.js"
  data-site-key="YOUR_SITE_KEY"
  data-api-base="https://CHAT_HOST"
  defer
></script>
```

API keys never leave the server. The browser only sends `siteKey` + message.

## Quick start — Spring Boot product

1. Authenticate Maven to GitHub Packages (`settings.xml` server id `github`)
2. Add the starter dependency
3. Set `GROQ_API_KEY` (and optional `GEMINI_API_KEY`)
4. Include CSRF meta tags + portal widget CSS/JS in your layout

```xml
<dependency>
  <groupId>com.fts</groupId>
  <artifactId>fts-ai-chatbot-spring-boot-starter</artifactId>
  <version>0.2.0</version>
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

## Run the public chat API locally

```bash
mvn -pl chat-api -am -DskipTests package
java -jar chat-api/target/fts-ai-chat-api-0.2.0.jar
```

Then open http://localhost:8088/embed-demo.html (`siteKey`: `demo-site`).

```text
GROQ_API_KEY=...
GEMINI_API_KEY=...   # optional 429 fallback
PORT=8088            # optional
```

### Docker / VM deploy

```bash
docker build -t fts-ai-chat-api:local .
docker run --rm -p 8080:8080 -e GROQ_API_KEY=... fts-ai-chat-api:local
```

CI/CD (GitHub Actions → Docker Hub → VM): see [docs/CICD_GITHUB_DOCKER_VM.md](docs/CICD_GITHUB_DOCKER_VM.md).  
Compose template for the VM: [`deploy/docker-compose.yml`](deploy/docker-compose.yml).

## Publish a new library version

1. Bump `<version>` in the parent `pom.xml` (and module parents)
2. Create a GitHub Release (triggers **Publish GitHub Package**)
3. Consumers bump the starter dependency version

`chat-api` is not published to Packages (runnable service only).

## Local install (without Packages)

```bash
mvn -DskipTests clean install
```
