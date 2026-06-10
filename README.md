# StockOps API

Spring Boot backend for StockOps inventory management system.

## Overview

StockOps API is the backend service for the StockOps smart inventory management system.
It provides REST APIs for inventory tracking, purchase order management, AI-powered
demand forecasting, and comprehensive reporting.

## Features

- 📦 Inventory management with FIFO/FEFO support
- 📊 Analytics and reporting engine
- 🤖 AI-powered demand forecasting and reorder recommendations
- 🔐 Scoped authorization (Global/Center/Warehouse)
- 📈 Time-series analytics with scheduled aggregation

## Tech Stack

- Java 21
- Spring Boot 3.2+
- Spring Data JPA
- Flyway (database migrations)
- PostgreSQL
- JUnit 5 + Testcontainers

## Prerequisites

- Java 21+
- Maven 3.9+ (or use `./mvnw`)
- PostgreSQL 15+ (or Docker for Testcontainers)

## Installation

```bash
# Copy environment variables
cp .env.example .env
# Edit .env with your database credentials

# Run database migrations
./mvnw flyway:migrate

# Start application
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Build

```bash
./mvnw clean package
```

## Test

```bash
# Unit tests
./mvnw test

# Integration tests (requires Docker)
./mvnw verify
```

## API Documentation

When running locally:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI Spec: http://localhost:8080/v3/api-docs

## Database

Flyway migrations are in `src/main/resources/db/migration/`.

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `stockops` |
| `DB_USER` | Database user | `stockops` |
| `DB_PASSWORD` | Database password | (required) |
| `JWT_SECRET` | JWT signing secret | (required) |

## AI Provider Configuration

`stockops.ai.bedrock.enabled` and `stockops.ai.vertex.enabled` default to `false`.
Set them to `true` in your deployment environment and provide the required credentials.

| Variable | Description |
|----------|-------------|
| `STOCKOPS_BEDROCK_ENABLED` | Set `true` to enable Amazon Bedrock |
| `STOCKOPS_BEDROCK_REGION` | AWS region (default: `ap-northeast-2`) |
| `STOCKOPS_BEDROCK_MODEL_ID` | Bedrock model or inference profile ARN |
| `STOCKOPS_VERTEX_ENABLED` | Set `true` to enable GCP Vertex AI fallback |
| `STOCKOPS_VERTEX_PROJECT_ID` | GCP project ID |
| `STOCKOPS_VERTEX_CREDENTIALS_JSON` | GCP service account JSON (base64 or raw) |
| `STOCKOPS_AI_SERVICE_API_KEY` | API key for stockops-ai-module Prophet service |

### Bedrock Live Smoke Tests

To run live integration tests against real Bedrock infrastructure:

```bash
STOCKOPS_BEDROCK_ENABLED=true \
STOCKOPS_BEDROCK_LIVE_TESTS=true \
STOCKOPS_BEDROCK_REGION=ap-northeast-2 \
STOCKOPS_BEDROCK_MODEL_ID=<model-id-or-inference-profile> \
mvn -Dgroups=bedrock-live test
```

These tests are disabled by default and do not run in CI unless `STOCKOPS_BEDROCK_LIVE_TESTS=true`.

## Related Repositories

- [stockops-web](https://github.com/your-org/stockops-web) — React frontend
- [stockops-legacy](https://github.com/your-org/stockops) — Original monorepo (frozen)

## License

Private — All rights reserved.
## Environment And Secrets

See [docs/ENVIRONMENT_SETUP.md](docs/ENVIRONMENT_SETUP.md) for local `.env`, GitHub Actions secrets, and deployment environment setup.

Never commit `.env`, real credentials, Terraform state, or AI-agent local configuration files.
