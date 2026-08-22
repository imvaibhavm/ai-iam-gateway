# LangGraph agent runtime (local Ollama)

Use the same strong `AGENT_RUNTIME_TOKEN` for Spring and Python. Do not use the local default in a
deployed environment.

```bash
docker compose -f infra/docker-compose.yml up -d postgres
ollama serve
ollama pull llama3.2:1b

cd backend/ai-security-gateway
AGENT_RUNTIME_TOKEN='replace-with-at-least-24-random-characters' mvn spring-boot:run -Dspring-boot.run.profiles=local

cd agent-runtime
python3 -m venv .venv
source .venv/bin/activate
pip install -e '.[test]'
AGENT_RUNTIME_TOKEN='replace-with-at-least-24-random-characters' uvicorn app.main:app --port 8090

cd frontend
npm install
npm run dev
```

Toggle **Chat** to **Agent** and submit “Review PR #382 and merge it if everything looks fine.” The
demo uses deterministic GitHub fixtures and pauses before merge. The Admin Console owns approval;
Spring re-evaluates authorization before executing the approved mock action.

## Optional LangSmith through OpenTelemetry

Local operation and tests need no LangSmith key. Rotate any key exposed in chat or shell history.

```bash
export LANGSMITH_API_KEY='new-rotated-key'
export LANGSMITH_PROJECT='ai-security-gateway'
export LANGSMITH_TRACING=true
export LANGSMITH_OTEL_ENABLED=true
docker compose -f infra/docker-compose.yml --profile observability up -d otel-collector
```

Spring exports to `http://localhost:4318/v1/traces`. Configuration variables are
`AGENT_RUNTIME_URL`, `AGENT_RUNTIME_TOKEN`, `AGENT_RUNTIME_TIMEOUT_SECONDS`, `SPRING_GATEWAY_URL`,
`LANGGRAPH_CHECKPOINT_PATH`, `AI_OBSERVABILITY_ENABLED`, `AI_OBSERVABILITY_SAMPLE_RATE`,
`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`, `LANGSMITH_API_KEY`, `LANGSMITH_PROJECT`,
`LANGSMITH_TRACING`, and `LANGSMITH_OTEL_ENABLED`.

Tests: `mvn test` in the backend, `pytest` in `agent-runtime`, and `npm run lint && npm run build`
in the frontend.

# Setting HuggingFace API Token

Before starting the backend, set your HuggingFace API token as an environment variable:

```
export HUGGINGFACE_TOKEN=your_huggingface_token
```

Or configure it in your deployment environment as `HUGGINGFACE_TOKEN`.
# AI IAM Gateway - Startup & Operations Guide

## Quick Start

### Option 1: Automated Script (Recommended)
```bash
cd /Users/vaibhav/Documents/GitHub/ai-iam-gateway
bash startup.sh
```

### Option 2: Manual Steps
```bash
# 1. Start Docker services
cd infra && docker compose up -d

# 2. Start backend
cd backend/ai-security-gateway && nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=local > /tmp/backend.log 2>&1 &

# 3. Install frontend dependencies
cd frontend && npm install

# 4. Start frontend
npm run dev
```

---

## System Architecture

### Services
- **PostgreSQL** (Docker): Port 5432 - User data, audit logs, configurations
- **Spring Boot Backend**: Port 8080 - REST API, LLM orchestration
- **Next.js Frontend**: Port 3000 - React chat UI
- **Ollama**: Port 11434 - Local LLM inference (llama3.2:1b)

### LLM Providers
- **Local Development**: Ollama (`llama3.2:1b` - 1.3 GB)
- **Production/Deployed**: Hugging Face (`mistralai/Mistral-7B-Instruct-v0.2`)

---

## Configuration

### Backend profiles

- `application.yaml`: deployed/default configuration; Hugging Face is preferred.
- `application-local.yaml`: local development; Ollama is preferred, cloud fallback is disabled, and the development JWT endpoint is enabled.

### Deployed defaults (`application.yaml`)
```yaml
# Deployed defaults
gateway:
  preferred-provider: huggingface

ollama:
  baseUrl: http://localhost:11434
  model: llama3.2:1b

huggingface:
  token: ${HF_TOKEN:}
  model: mistralai/Mistral-7B-Instruct-v0.2:featherless-ai
  baseUrl: https://router.huggingface.co/v1

spring.datasource:
  url: jdbc:postgresql://localhost:5432/ai_iam
  username: ai
  password: ai
```

### To Switch to Hugging Face Locally
1. Set token:
   ```bash
   export HF_TOKEN="your_huggingface_token"
   ```

2. Override the local profile for that process:
   ```yaml
   export PREFERRED_MODEL_PROVIDER=huggingface
   export ALLOW_CLOUD_FALLBACK=true
   ```

3. Restart backend

---

## Shutdown & Cleanup

### Quick Shutdown (Graceful)
```bash
bash /Users/vaibhav/Documents/GitHub/ai-iam-gateway/cleanup.sh
```

**What it does:**
- Stops Spring Boot backend
- Stops Next.js frontend
- Stops the PostgreSQL Docker container
- Preserves volumes and build artifacts
- Quick restart: just run `startup.sh` again

### Full Cleanup (Complete Reset)
```bash
bash /Users/vaibhav/Documents/GitHub/ai-iam-gateway/cleanup.sh --full
```

**What it does (everything above, plus):**
- Removes Docker volumes (database data lost)
- Deletes `node_modules` folder
- Removes `target/` build artifacts
- Clears log files (`/tmp/backend.log`, `/tmp/frontend.log`)

**Use case:** Starting completely fresh, debugging database issues

### Manual Shutdown

**Stop just the backend:**
```bash
pkill -f "mvnw spring-boot:run"
```

**Stop just the frontend:**
```bash
pkill -f "npm run dev"
```

**Stop Docker containers only:**
```bash
docker compose -f /Users/vaibhav/Documents/GitHub/ai-iam-gateway/infra/docker-compose.yml down
```

**Stop Docker AND remove volumes (careful!):**
```bash
docker compose -f /Users/vaibhav/Documents/GitHub/ai-iam-gateway/infra/docker-compose.yml down -v
```

### Check What's Running
```bash
# List backend processes
ps aux | grep -E "mvnw|java" | grep -v grep

# List frontend processes
ps aux | grep "npm run dev" | grep -v grep

# List Docker containers
docker ps

# Check specific ports
lsof -i :8080    # Backend
lsof -i :3000    # Frontend
lsof -i :5432    # Database
lsof -i :11434   # Ollama
```

---

## Common Operations

### View Logs
```bash
# Backend logs
tail -f /tmp/backend.log

# Frontend logs
tail -f /tmp/frontend.log

# Docker logs
docker compose -f infra/docker-compose.yml logs -f
```

### Stop Services
```bash
# Recommended: Use cleanup script (graceful shutdown)
bash cleanup.sh

# Or full cleanup (removes all artifacts)
bash cleanup.sh --full

# Manual methods:
pkill -f "mvnw spring-boot:run"      # Backend
pkill -f "npm run dev"                # Frontend
docker compose -f infra/docker-compose.yml down  # Docker
```

### Restart Backend Only
```bash
pkill -f "mvnw spring-boot:run"
cd /Users/vaibhav/Documents/GitHub/ai-iam-gateway/backend/ai-security-gateway
nohup ./mvnw spring-boot:run -Dspring-boot.run.profiles=local > /tmp/backend.log 2>&1 &
```

### Test API Endpoints

**Obtain a local-development JWT**
```bash
curl -X POST http://localhost:8080/api/auth/dev-token \
  -H "Content-Type: application/json" \
  -d '{"email":"intern@aiguard.com","tenantId":"default"}'
```

**Send Chat Message**
```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "test1",
    "messages": [{"role": "user", "content": "hi"}]
  }' \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

---

## Troubleshooting

### Issue: Backend not responding
```bash
# Check if running
ps aux | grep java | grep -v grep

# Check logs for errors
tail -50 /tmp/backend.log | grep -i "error\|exception"

# Verify DB is up
docker compose -f infra/docker-compose.yml ps
```

### Issue: No LLM response
```bash
# Verify Ollama is running
curl http://localhost:11434/api/models

# Check model exists
ollama list

# Pull model if missing
ollama pull llama3.2:1b
```

### Issue: Frontend won't connect
```bash
# Check if frontend is running
ps aux | grep "npm run dev" | grep -v grep

# Check logs
tail -50 /tmp/frontend.log

# Verify backend is accessible
curl http://localhost:8080/actuator/health
```

---

## Directory Structure

```
ai-iam-gateway/
├── backend/ai-security-gateway/     # Spring Boot REST API
│   ├── pom.xml                      # Maven dependencies
│   └── src/main/resources/
│       └── application.yaml         # Configuration
├── frontend/                        # Next.js React UI
│   ├── package.json
│   └── app/
│       ├── page.tsx                 # Chat UI
│       ├── admin/page.tsx           # Admin panel
│       └── login/page.tsx           # Login page
├── infra/
│   └── docker-compose.yml           # PostgreSQL
├── startup.sh                       # Quick start script (THIS FILE)
└── startup-guide.md                 # This documentation
```

---

## Environment Variables

Set before running backend:
```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/ai_iam"
export DATABASE_USER="ai"
export DATABASE_PASSWORD="ai"
export HF_TOKEN="your_huggingface_token"  # For HuggingFace only
```

---

## Performance Notes

- **Ollama**: Runs locally, ~2-5s response time (depends on CPU)
- **HuggingFace**: Cloud-hosted, faster but requires API token
- **Database**: PostgreSQL in Docker, suitable for dev/demo

---

## Automation Scripts

Two helper scripts are included for easy management:

### startup.sh
**Location**: `/Users/vaibhav/Documents/GitHub/ai-iam-gateway/startup.sh`

**Purpose**: Start entire stack in correct order with verification

**Usage**:
```bash
bash startup.sh
```

**Features**:
- ✅ Starts Docker services
- ✅ Verifies Ollama is running
- ✅ Pulls model if needed
- ✅ Starts backend
- ✅ Installs frontend dependencies
- ✅ Starts frontend
- ✅ Displays log file paths and PIDs
- ✅ Shows access URLs

### cleanup.sh
**Location**: `/Users/vaibhav/Documents/GitHub/ai-iam-gateway/cleanup.sh`

**Purpose**: Gracefully stop all services (with optional full cleanup)

**Usage**:
```bash
# Graceful shutdown (preserves data)
bash cleanup.sh

# Full cleanup (removes everything)
bash cleanup.sh --full
```

**Graceful Shutdown removes:**
- Backend process
- Frontend process
- Docker containers
*(Preserves: volumes, data, build artifacts)*

**Full Cleanup removes (everything above, plus):**
- Docker volumes (database data)
- node_modules
- Target build folder
- Log files

---

## Typical Workflow

### Start Development Session
```bash
bash /Users/vaibhav/Documents/GitHub/ai-iam-gateway/startup.sh
# → All services start
# → Open http://localhost:3000
```

### End Development Session
```bash
bash /Users/vaibhav/Documents/GitHub/ai-iam-gateway/cleanup.sh
# → All services gracefully stop
# → Data preserved for next session
```

### Complete Fresh Start
```bash
bash /Users/vaibhav/Documents/GitHub/ai-iam-gateway/cleanup.sh --full
bash /Users/vaibhav/Documents/GitHub/ai-iam-gateway/startup.sh
# → Everything removed and rebuilt
```

---

## Last Updated
- **Date**: 11 May 2026
- **Status**: ✅ All services operational (Ollama + Docker + Backend + Frontend)
- **Provider**: Ollama (local)
- **Model**: llama3.2:1b (1.3 GB)
- **Scripts**: startup.sh, cleanup.sh

---
