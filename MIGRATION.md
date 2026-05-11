# Repository Migration Guide

## History

This repository was extracted from the [StockOps monorepo](https://github.com/your-org/stockops)
on 2026-05-11 using `git filter-branch`.

## What Changed

- Extracted `backend/` directory to repository root
- Preserved all git history (68 commits)
- Added standalone `.gitignore`, `.env.example`, and CI/CD configuration
- Removed Pi-specific `application-pi.yml`
- Secrets are now environment variables (no hardcoded values)

## Local Development Setup

### 1. Clone this repository

```bash
git clone https://github.com/your-org/stockops-api.git
cd stockops-api
```

### 2. Configure environment

```bash
cp .env.example .env
# Edit .env with your database credentials
```

### 3. Start database

```bash
docker run -d --name stockops-db \
  -e POSTGRES_DB=stockops \
  -e POSTGRES_USER=stockops \
  -e POSTGRES_PASSWORD=your_password \
  -p 5432:5432 \
  postgres:15
```

### 4. Run migrations and start app

```bash
./mvnw flyway:migrate
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Migration from Monorepo

If you were working in the monorepo:

1. Your backend code is now here
2. Flyway migrations remain in `src/main/resources/db/migration/`
3. All API endpoints remain the same (`/api/v1/...`)
4. Update your IDE project path from `backend/` to repository root
