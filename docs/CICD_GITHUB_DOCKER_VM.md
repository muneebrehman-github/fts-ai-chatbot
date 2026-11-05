# CI/CD: GitHub Actions → Docker Hub → VM deploy

Reusable runbook for this project and other apps.  
**Pattern:** push to `main` → build image on GitHub → push to Docker Hub → SSH into VM → `docker compose pull` + restart.

Reference implementation: Alfa Pearl portal  
- Workflow: `.github/workflows/deploy.yml`  
- VM app dir: `/home/muneeb/alfapearl`  
- Image: `muneeb123/alfapearl-portal:latest`

---

## Architecture

```
Developer push (main)
        │
        ▼
GitHub Actions (ubuntu-latest)
        │  1) docker build (Dockerfile)
        │  2) docker push → Docker Hub
        ▼
SSH to VM (deploy user + private key)
        │  3) cd app dir
        │  4) docker compose pull app
        │  5) docker compose up -d --no-deps app
        ▼
Running container on VM (env from VM .env)
```

**Do not** put DB passwords / JWT secrets in GitHub. Keep them in the VM `.env`.

---

## Prerequisites

### App / repo

- [ ] Dockerfile that builds a runnable image
- [ ] VM already running the app via Docker Compose
- [ ] Compose `app` service uses **`image:`** (registry), **not** `build:`
- [ ] Image name on VM matches what Actions will push

Example VM `docker-compose.yml`:

```yaml
services:
  app:
    image: YOUR_DOCKERHUB_USER/YOUR_IMAGE:latest
    container_name: your-app
    restart: unless-stopped
    environment:
      # from .env — app secrets stay on the VM
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      # ...
    ports:
      - "${SERVER_PORT:-8080}:8080"
```

### VM

- [ ] Docker + Docker Compose plugin installed
- [ ] Deploy user can run Docker (`usermod -aG docker USER` then re-login)
- [ ] SSH listening on `0.0.0.0:22` (`ss -tlnp | grep :22`)
- [ ] Port **22 reachable from the public internet** (GitHub runners are not your home IP)

```bash
# On VM
sudo ss -tlnp | grep :22
groups   # should include docker for deploy user
```

```powershell
# From your PC — must be True for Actions SSH to work
Test-NetConnection YOUR_VM_IP -Port 22
```

If UFW is inactive but Actions still times out, check the **hoster panel firewall** (Contabo / DigitalOcean / etc.).  
Do **not** create an empty cloud firewall without an allow rule for TCP 22 — empty firewalls drop inbound traffic.

---

## Step 1 — SSH deploy key

Generate on your **local PC** (PowerShell: empty passphrase needs special quoting):

```powershell
ssh-keygen -t ed25519 -C "github-actions-deploy" -f deploy-key -N '""'
```

| File | Where it goes |
|------|----------------|
| `deploy-key` (private) | GitHub secret `VM_SSH_KEY` |
| `deploy-key.pub` (public) | VM user `~/.ssh/authorized_keys` |

Install public key for the **same user** you will set as `VM_USER` (e.g. `muneeb`, not `root`):

```bash
# As root is OK for setup — write into the deploy user's home
mkdir -p /home/DEPLOY_USER/.ssh
chmod 700 /home/DEPLOY_USER/.ssh
# paste ONE line from deploy-key.pub into authorized_keys
nano /home/DEPLOY_USER/.ssh/authorized_keys
chmod 600 /home/DEPLOY_USER/.ssh/authorized_keys
chown -R DEPLOY_USER:DEPLOY_USER /home/DEPLOY_USER/.ssh
chmod go-w /home/DEPLOY_USER
```

Test from PC (must **not** ask for password):

```powershell
ssh -i .\deploy-key -o IdentitiesOnly=yes DEPLOY_USER@YOUR_VM_IP
```

**Common failure:** key added under `root` but Actions connects as `muneeb` → password prompt / key rejected. Put the key in `/home/muneeb/.ssh/authorized_keys`.

---

## Step 2 — GitHub repository secrets

Repo → **Settings** → **Secrets and variables** → **Actions** → **Repository secrets**  
(Use Environment secrets only if you need staging/prod gates.)

| Secret | Example / notes |
|--------|------------------|
| `VM_HOST` | `79.143.176.57` — IP only, no `ssh://`, no port |
| `VM_USER` | `muneeb` |
| `VM_SSH_KEY` | Full private key including `BEGIN` / `END` lines |
| `VM_APP_DIR` | `/home/muneeb/alfapearl` — folder with `docker-compose.yml` |
| `VM_PORT` | Optional; default 22 |
| `DOCKERHUB_USERNAME` | Docker Hub user/namespace (e.g. `muneeb123`) |
| `DOCKERHUB_TOKEN` | Access token with **Read & Write** (not Read-only) |

Create token: [hub.docker.com](https://hub.docker.com) → Account Settings → **Personal access tokens** → **Read & Write**.

**Error:** `unauthorized: access token has insufficient scopes` → recreate token with write permission and update `DOCKERHUB_TOKEN`.

---

## Step 3 — Workflow file

Create `.github/workflows/deploy.yml` (adapt names/paths):

```yaml
name: Build and deploy portal

on:
  push:
    branches: [main]
    paths:
      - "PATH_TO_APP/**"           # e.g. alfapearl-portal/**
      - ".github/workflows/deploy.yml"
  workflow_dispatch:               # allow manual Run workflow

env:
  IMAGE_NAME: YOUR_DOCKERHUB_USER/YOUR_IMAGE

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@v4

      - uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.IMAGE_NAME }}
          tags: |
            type=raw,value=latest
            type=sha,prefix=

      - uses: docker/build-push-action@v6
        with:
          context: ./PATH_TO_APP          # folder that contains Dockerfile
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy on VM
        uses: appleboy/ssh-action@v1.2.0
        with:
          host: ${{ secrets.VM_HOST }}
          username: ${{ secrets.VM_USER }}
          key: ${{ secrets.VM_SSH_KEY }}
          # port: ${{ secrets.VM_PORT }}   # if not 22
          script: |
            set -e
            cd "${{ secrets.VM_APP_DIR }}"
            docker compose pull app
            docker compose up -d --no-deps app
            docker image prune -f
```

Notes:

- Service name in compose must be `app` (or change `pull` / `up` to match).
- `--no-deps` restarts only the app; DB containers stay up.
- Match `IMAGE_NAME` to the VM compose `image:` line.

---

## Step 4 — Align image names

| Place | Must match |
|-------|------------|
| Workflow `IMAGE_NAME` | e.g. `muneeb123/alfapearl-portal` |
| VM compose `image:` | e.g. `muneeb123/alfapearl-portal:latest` |
| Docker Hub repo | Must exist (or will be created on first push if allowed) |

Alfa Pearl VM compose (no `ALFAPEARL_IMAGE` required when hardcoded):

```yaml
image: muneeb123/alfapearl-portal:latest
```

---

## Step 5 — Run and verify

1. Commit + push workflow to `main`, **or** Actions → workflow → **Run workflow**
2. Do **not** use GitHub’s “suggested” template workflows if you already have `deploy.yml`
3. Watch jobs: **build-and-push** then **deploy**
4. On VM:

```bash
cd /home/muneeb/alfapearl   # or your VM_APP_DIR
docker compose ps
docker compose logs --tail=80 app
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| SSH asks for password with `-i` key | Public key missing/wrong user | Put `.pub` in that user’s `authorized_keys`; fix perms `700` / `600` |
| `Offering public key` then still password | Key not for that account | Check `/home/USER/.ssh/authorized_keys`, not `/root/...` |
| `access token has insufficient scopes` | Docker Hub token Read-only | New token **Read & Write** |
| `dial tcp …:22: i/o timeout` | Port 22 not reachable from GitHub | Open 22 publicly; fix hoster firewall; verify `Test-NetConnection` |
| UFW inactive but still timeout | Panel/network firewall | Contabo/DO firewall / security group |
| Deploy OK but old app | Compose uses `build:` | Switch to `image:` matching Hub |
| Pull denied on VM | Private Hub image | `docker login` on VM, or make repo public |
| Wrong suggested Actions page | Looking at templates | Open existing workflow in left sidebar |

### PowerShell `ssh-keygen` empty passphrase

```powershell
# BAD on PowerShell: -N ""
# GOOD:
ssh-keygen -t ed25519 -C "github-actions-deploy" -f deploy-key -N '""'
```

---

## Checklist — new application

Copy this for each new app:

1. [ ] Dockerfile + local `docker compose` with `image: USER/APP:latest`
2. [ ] App running on VM; note `VM_APP_DIR` and compose service name
3. [ ] Deploy user + SSH key (public on VM, private in `VM_SSH_KEY`)
4. [ ] Port 22 open to internet; `Test-NetConnection` True
5. [ ] Docker Hub repo + **Read & Write** token
6. [ ] GitHub secrets: `VM_*` + `DOCKERHUB_*`
7. [ ] `.github/workflows/deploy.yml` with correct `IMAGE_NAME` + `context`
8. [ ] Push to `main` or **Run workflow**
9. [ ] Confirm new image on Hub and new container on VM

---

## Cursor / agent instructions

When the user asks to “set up CI/CD like Alfa Pearl” or “follow the deploy runbook”:

1. Read this file: `docs/CICD_GITHUB_DOCKER_VM.md`
2. Inspect the app’s Dockerfile and any existing compose
3. Prefer **Docker Hub + SSH deploy** if the VM already uses a Hub `image:`
4. Create `.github/workflows/deploy.yml` from the template above (do not use unrelated GitHub template suggestions)
5. Tell the user which **secrets** to create (never invent real keys/tokens)
6. Remind: public key → VM deploy user; private key → `VM_SSH_KEY`; Hub token must be **Read & Write**
7. Remind: verify `Test-NetConnection HOST -Port 22` before first deploy
8. Do not commit secrets, `.env`, or private keys
9. Commit workflow only when the user asks; push when they ask

### Alfa Pearl specifics (current)

| Item | Value |
|------|--------|
| Workflow | `.github/workflows/deploy.yml` |
| Image | `muneeb123/alfapearl-portal` |
| Build context | `./alfapearl-portal` |
| VM user | `muneeb` |
| VM app dir | `/home/muneeb/alfapearl` |
| Compose service | `app` |
| Trigger | push `main` (portal paths) + `workflow_dispatch` |

### FTS AI Chatbot (chat-api) specifics

| Item | Value |
|------|--------|
| Workflow | `.github/workflows/deploy.yml` |
| Dockerfile | repo root `Dockerfile` |
| Build context | `.` (multi-module: core + chat-api) |
| Image | `$DOCKERHUB_USERNAME/fts-ai-chat-api` |
| Compose template | `deploy/docker-compose.yml` (copy to VM) |
| Env template | `deploy/.env.example` → VM `.env` |
| Suggested `VM_APP_DIR` | `/home/muneeb/fts-ai-chatbot` |
| Compose service | `app` |
| Container port | `8080` (`/health`, `/widget.js`, public chat API) |
| Trigger | push `main` (core/chat-api/Dockerfile/…) + `workflow_dispatch` |

**VM one-time setup**

```bash
mkdir -p /home/muneeb/fts-ai-chatbot
# copy deploy/docker-compose.yml here; create .env from deploy/.env.example
# set GROQ_API_KEY (and optional GEMINI_API_KEY)
# ensure image: line matches Docker Hub: USER/fts-ai-chat-api:latest
docker compose up -d
```

GitHub secrets (same pattern as Alfa Pearl): `VM_HOST`, `VM_USER`, `VM_SSH_KEY`, `VM_APP_DIR`, `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`.

---

## Optional alternatives (not used here)

| Approach | When |
|----------|------|
| GHCR (`ghcr.io/...`) | Prefer GitHub Packages; VM needs `docker login ghcr.io` if private |
| Self-hosted runner on VM | Cannot open SSH 22 to the internet |
| Build on VM | Slower; skip Hub — not recommended if Hub already works |
