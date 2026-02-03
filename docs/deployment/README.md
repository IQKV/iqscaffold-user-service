## 📜 Deployment Guide

### Overview

The IQ Scaffold User Service is deployed using Helm charts and automated CI/CD pipelines. The service provides JWT-based authentication, multi-tenancy, and user management capabilities.

### Prerequisites

- Kubernetes 1.19+
- Helm 3.2.0+
- External infrastructure services (PostgreSQL, Redis, RabbitMQ, S3/MinIO)

### Environments

| Environment | Namespace                   | Purpose                      |
| ----------- | --------------------------- | ---------------------------- |
| Dev         | `iqscaffold-dev-env`        | Development and WIP branches |
| Test        | `iqscaffold-test-env`       | Feature branch testing       |
| Staging     | `iqscaffold-staging-env`    | Pre-production validation    |
| Production  | `iqscaffold-production-env` | Live production environment  |

### Automated Deployment (CI/CD)

#### Drone Pipeline Overview

The service uses a comprehensive Drone CI/CD pipeline with 10 stages:

1. **VerifyCode** - Code quality, tests, static analysis
2. **PublishArtifacts** - Maven artifacts to Nexus
3. **PublishDockerImage** - Container images to registry
4. **DeployWorkInProgressOnDev** - WIP branch auto-deployment
5. **RollbackWorkInProgressOnDev** - WIP rollback
6. **PromoteFeatureDeployment** - Feature branch promotion
7. **RollbackFeatureDeployment** - Feature rollback
8. **PromoteDeployment** - Release promotion
9. **RollbackDeployment** - Release rollback
10. **ReleasePackage** - Automated version management

#### Branch Deployment Strategy

| Branch Type | Auto Deploy | Manual Promote | Target Environment |
| ----------- | ----------- | -------------- | ------------------ |
| `wip`       | ✅ Dev      | -              | Dev                |
| `feature/*` | -           | ✅ Test        | Test               |
| `dev`       | -           | ✅ Staging     | Staging            |
| Tags        | -           | ✅ Production  | Production         |

#### Deployment Commands

The pipeline uses these Helm commands for deployment:

```bash
# Development (WIP branches)
helm upgrade --install --atomic --wait --timeout 5m iqscaffold-user-service ./ \
  --values ./values.yaml \
  --values ./values-dev.yaml \
  --set image.tag=wip \
  --set infraServices.postgresql.password=${INFRA_POSTGRESQL_PASSWORD} \
  --set infraServices.rabbitmq.password=${INFRA_RABBITMQ_PASSWORD} \
  --set config.jwt.secretKey=${JWT_SECRET_KEY} \
  --namespace iqscaffold-dev-env

# Production (Tagged releases)
helm upgrade --install --atomic --wait --timeout 5m iqscaffold-user-service ./ \
  --values ./values.yaml \
  --values ./values-production.yaml \
  --set image.tag=${DRONE_TAG} \
  --set infraServices.postgresql.password=${INFRA_POSTGRESQL_PASSWORD} \
  --set infraServices.rabbitmq.password=${INFRA_RABBITMQ_PASSWORD} \
  --set config.jwt.secretKey=${JWT_SECRET_KEY} \
  --namespace iqscaffold-production-env
```

### Manual Deployment

#### Quick Start

```bash
# Clone Helm charts
git clone <HELM_CHARTS_REPOSITORY> charts
cd charts/IQKV/iqscaffold-user-service

# Deploy to development
helm upgrade --install user-service ./ \
  --values values-dev.yaml \
  --set infraServices.postgresql.password="your-db-password" \
  --set config.jwt.secretKey="your-jwt-secret" \
  --namespace iqscaffold-dev-env \
  --create-namespace
```

#### Environment-Specific Deployments

#### Development

```bash
helm upgrade --install user-service ./ \
  --values values-dev.yaml \
  --namespace iqscaffold-dev-env \
  --create-namespace
```

#### Production

```bash
helm upgrade --install user-service ./ \
  --values values-production.yaml \
  --set infraServices.postgresql.password="${DB_PASSWORD}" \
  --set infraServices.redis.password="${REDIS_PASSWORD}" \
  --set infraServices.rabbitmq.password="${RABBITMQ_PASSWORD}" \
  --set infraServices.s3.accessKey="${S3_ACCESS_KEY}" \
  --set infraServices.s3.secretKey="${S3_SECRET_KEY}" \
  --set config.jwt.secretKey="${JWT_SECRET_KEY}" \
  --set config.email.smtp.username="${SMTP_USERNAME}" \
  --set config.email.smtp.password="${SMTP_PASSWORD}" \
  --namespace iqscaffold-production-env \
  --create-namespace
```

### Configuration

#### Required Secrets

| Secret            | Environment Variable       | Required | Description                 |
| ----------------- | -------------------------- | -------- | --------------------------- |
| Database Password | `INFRA_POSTGRESQL_PASSWORD`  | ✅       | PostgreSQL password         |
| JWT Secret        | `JWT_SECRET_KEY`           | ✅       | JWT signing key (256+ bits) |
| RabbitMQ Password | `INFRA_RABBITMQ_PASSWORD` | ⚠️       | Message broker password     |
| Redis Password    | `REDIS_PASSWORD`           | ⚠️       | Cache password              |
| S3 Access Key     | `S3_ACCESS_KEY`            | ⚠️       | Object storage access       |
| S3 Secret Key     | `S3_SECRET_KEY`            | ⚠️       | Object storage secret       |
| SMTP Username     | `SMTP_USERNAME`            | ⚠️       | Email service username      |
| SMTP Password     | `SMTP_PASSWORD`            | ⚠️       | Email service password      |

#### External Services

The service connects to these external infrastructure components:

- **PostgreSQL**: User data storage
- **Redis**: Session caching and rate limiting
- **RabbitMQ**: Event messaging
- **S3/MinIO**: Avatar and file storage
- **SMTP**: Email notifications

#### Service Configuration

| Setting        | Dev      | Production       |
| -------------- | -------- | ---------------- |
| Replicas       | 1        | 2                |
| CPU Request    | 200m     | 500m             |
| Memory Request | 256Mi    | 512Mi            |
| Autoscaling    | Disabled | 2-10 replicas    |
| Ingress        | Disabled | Enabled with TLS |
| Monitoring     | Disabled | Enabled          |

### Monitoring & Health Checks

#### Health Endpoints

- **Liveness**: `/actuator/health/liveness` (port 8081)
- **Readiness**: `/actuator/health/readiness` (port 8081)
- **Metrics**: `/actuator/prometheus` (port 8081)

#### Monitoring Stack

Production deployments include:

- Prometheus ServiceMonitor
- Alerting rules for service health
- Grafana dashboards

### Troubleshooting

#### Common Issues

1. **Database Connection Failures**

   ```bash
   kubectl logs deployment/iqscaffold-user-service -n iqscaffold-dev-env
   ```

2. **Check Configuration**

   ```bash
   kubectl describe configmap iqscaffold-user-service-config -n iqscaffold-dev-env
   ```

3. **Test Health Endpoints**
   ```bash
   kubectl port-forward deployment/iqscaffold-user-service 8081:8081 -n iqscaffold-dev-env
   curl http://localhost:8081/actuator/health
   ```

#### Rollback

```bash
# Rollback to previous version
helm rollback iqscaffold-user-service -n iqscaffold-production-env

# Or uninstall completely
helm uninstall iqscaffold-user-service -n iqscaffold-production-env
```

### Security

- All sensitive values passed via `--set` flags
- TLS enabled in production
- Network policies restrict pod communication
- Non-root container execution
- Read-only root filesystem in production
