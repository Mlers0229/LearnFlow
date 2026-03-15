# LearnFlow

LearnFlow is an AI-driven learning planning platform that connects goal understanding, plan generation, resource recommendation, exercise review, chat tutoring, and admin-side model management into one workflow.

## Highlights

- Generate structured study plans from natural-language goals
- Review historical plans with day-level task, resource, and exercise linkage
- Save exercise answers, AI scoring, feedback, and review records
- Upload learning resources and manage approval status
- Use AI chat for tutoring and follow-up learning support
- Manage models and dashboard metrics from an admin console

## Stack

- Frontend: Vue 3, Vite, Naive UI
- Backend: Spring Boot 3, JPA, PostgreSQL
- Agent Platform: FastAPI, Uvicorn
- AI Layer: multi-agent orchestration with OpenAI-compatible LLM endpoints

## Repository Structure

```text
LearnFlow/
├─ frontend/         # Vue user/admin web app
├─ backend/          # Spring Boot API server
├─ agent-platform/   # FastAPI multi-agent service
├─ docs/             # design, progress, deployment docs
└─ scripts/          # Linux deploy and rollback scripts
```

## Main Modules

### User Side

- Study plan generation
- Historical plan workbench
- Exercise review
- AI chat
- Resource upload
- Profile and authentication

### Admin Side

- Dashboard
- Resource management
- Model configuration
- User management
- Agent log inspection

## Current Capabilities

- User-authenticated study plan generation and persistence
- Per-user historical plan browsing and day-level execution actions
- Resource recommendation for both whole plans and single study days
- Exercise generation, evaluation, and review record persistence
- Resource feedback collection for recommendation improvement
- Admin dashboard wired to real backend aggregate data
- Admin-side model catalog refresh and model config proxying
- Linux deployment and rollback scripts for single-server setup

## Quick Start

### 1. Frontend

```bash
cd frontend
npm install
npm run dev
```

Default dev URL: `http://localhost:5173`

### 2. Backend

Requirements:

- Java 17
- Maven
- PostgreSQL

```bash
cd backend
mvn spring-boot:run
```

Default port: `18081`

Main config file:

- [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml)

### 3. Agent Platform

Requirements:

- Python 3.11

```bash
cd agent-platform
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Default port: `8000`

## Configuration Notes

### Backend

The backend reads database and agent endpoint settings from Spring configuration and environment variables.

Important variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `LEARNFLOW_AI_AGENT_BASE_URL`

### Agent Platform

The agent platform reads runtime model settings from environment variables or local runtime config.

Important variables:

- `LEARNFLOW_DB_URL`
- `LLM_API_BASE`
- `LLM_API_KEY`
- `LLM_API_MODEL`
- `ENABLE_LLM_PLAN`

Do not commit real API keys or local runtime secrets.

## Linux Deployment

Deployment and rollback scripts are already included:

- [`scripts/deploy-linux.sh`](scripts/deploy-linux.sh)
- [`scripts/rollback-linux.sh`](scripts/rollback-linux.sh)
- [`scripts/learnflow.env.example`](scripts/learnflow.env.example)

Detailed guide:

- [`docs/linux-deploy.md`](docs/linux-deploy.md)

Typical usage:

```bash
cp scripts/learnflow.env.example scripts/learnflow.env
vim scripts/learnflow.env
chmod +x scripts/deploy-linux.sh scripts/rollback-linux.sh
sudo bash scripts/deploy-linux.sh scripts/learnflow.env
```

## Screens and Flows

The current product direction is centered around a closed-loop learning workflow:

1. User enters a goal
2. Agents generate a study plan
3. User executes daily tasks in the history workbench
4. System recommends resources and generates exercises
5. Exercise answers and AI feedback are persisted
6. User reviews weak points in the exercise review page
7. Admin manages resources, models, users, and dashboard metrics

## Docs

- [`docs/progress-report.md`](docs/progress-report.md)
- [`docs/db-design.md`](docs/db-design.md)
- [`docs/frontend-refactor-spec.md`](docs/frontend-refactor-spec.md)
- [`docs/course-design-report.md`](docs/course-design-report.md)

## Security Notes

- This repository has been cleaned of known hard-coded LLM keys before publication
- Database passwords should be injected through environment variables
- Agent runtime cache files and local secret files should stay untracked

## Roadmap Ideas

- Docker Compose deployment
- Production-ready env layering for staging and prod
- Better recommendation ranking using more user feedback signals
- More polished admin operations and monitoring
- Safer secret management and CI/CD automation

## License

No license has been added yet. If you plan to open-source this project for reuse, add a proper license before broader distribution.
