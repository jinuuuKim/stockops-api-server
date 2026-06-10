# Environment Setup

## Local Files

Use `.env.example` as the template. Copy it to `.env` locally and never commit `.env`.

```bash
cp .env.example .env
```

## Public Repository Rule

Public GitHub mirrors must contain examples only. Real values belong in one of:

- Local `.env`
- GitHub Actions repository secrets
- GitHub Actions environment secrets for `dev`, `test`, or `prod`
- Runtime secret stores such as AWS Secrets Manager, when the deployment target uses AWS

## GitHub Environments

- `dev`: automatic deployment allowed from `main` or manual workflow dispatch
- `test`: manual workflow dispatch or release candidate branches
- `prod`: manual workflow dispatch, protected branch or tag, required reviewer

## Secret Naming

Use upper snake case names:

- `AWS_REGION`
- `AWS_ROLE_TO_ASSUME`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `JWT_SECRET`
- `STOCKOPS_DATASOURCE_URL`
- `STOCKOPS_DATASOURCE_USERNAME`
- `STOCKOPS_DATASOURCE_PASSWORD`
- `VITE_API_BASE_URL`
- `VITE_MQTT_WS_URL`
- `VITE_MQTT_USERNAME`
- `VITE_MQTT_PASSWORD`
- `DATABASE_URL`
- `AI_MODULE_API_KEY`

## Values That Must Not Be Committed

- Real passwords
- Access tokens
- API keys
- Private keys
- RDS endpoints
- Secret Manager ARNs
- AWS account IDs
- Terraform state
- Personal local paths

## Metrics And Prometheus

The API server uses Spring Boot Actuator and the Micrometer Prometheus registry (both already present in `pom.xml`).

| Variable | Example | Notes |
| --- | --- | --- |
| `STOCKOPS_ACTUATOR_EXPOSURE` | `health,info,metrics,prometheus` | Exposes `/actuator/prometheus` when `prometheus` is included. |
| `STOCKOPS_ACTUATOR_HEALTH_SHOW_DETAILS` | `never` | Avoids exposing internals publicly. |
| `STOCKOPS_ENVIRONMENT` | `dev`, `test`, `prod` | Added as a common metrics tag (`environment`). |
| `STOCKOPS_ACTUATOR_PROMETHEUS_PUBLIC` | `false` | When `true`, `/actuator/prometheus` is reachable without authentication. Enable **only** behind private networking, a reverse-proxy allowlist, VPN, or an authenticated scraper. Default `false` keeps it authenticated. |

### Security policy chosen

- `/actuator/health` is public (load balancers).
- `/actuator/prometheus` requires authentication by default; set `STOCKOPS_ACTUATOR_PROMETHEUS_PUBLIC=true` only when the network layer already restricts access.

Example Prometheus scrape config:

```yaml
scrape_configs:
  - job_name: stockops-api-server
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
          - api-server.example.internal:8080
```

Use private networking, reverse proxy allowlists, VPN, or authenticated access for production metrics scraping.
