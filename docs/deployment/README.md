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
  --set infraServices.redis.password=${INFRA_REDIS_PASSWORD} \
  --set infraServices.rabbitmq.password=${INFRA_RABBITMQ_PASSWORD} \
  --set infraServices.s3.accessKey=${INFRA_S3_ACCESS_KEY} \
  --set infraServices.s3.secretKey=${INFRA_S3_SECRET_KEY} \
  --set config.jwt.secretKey=${JWT_SECRET_KEY} \
  --set config.oauth2.google.clientId=${GOOGLE_OAUTH2_CLIENT_ID} \
  --set config.oauth2.google.clientSecret=${GOOGLE_OAUTH2_CLIENT_SECRET} \
  --set config.email.smtp.username=${SMTP_USERNAME} \
  --set config.email.smtp.password=${SMTP_PASSWORD} \
  --namespace iqscaffold-dev-env

# Production (Tagged releases)
helm upgrade --install --atomic --wait --timeout 5m iqscaffold-user-service ./ \
  --values ./values.yaml \
  --values ./values-production.yaml \
  --set image.tag=${DRONE_TAG} \
  --set infraServices.postgresql.password=${INFRA_POSTGRESQL_PASSWORD} \
  --set infraServices.redis.password=${INFRA_REDIS_PASSWORD} \
  --set infraServices.rabbitmq.password=${INFRA_RABBITMQ_PASSWORD} \
  --set infraServices.s3.accessKey=${INFRA_S3_ACCESS_KEY} \
  --set infraServices.s3.secretKey=${INFRA_S3_SECRET_KEY} \
  --set config.jwt.secretKey=${JWT_SECRET_KEY} \
  --set config.oauth2.google.clientId=${GOOGLE_OAUTH2_CLIENT_ID} \
  --set config.oauth2.google.clientSecret=${GOOGLE_OAUTH2_CLIENT_SECRET} \
  --set config.email.smtp.username=${SMTP_USERNAME} \
  --set config.email.smtp.password=${SMTP_PASSWORD} \
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
  --set infraServices.redis.password="your-redis-password" \
  --set infraServices.rabbitmq.password="your-rabbitmq-password" \
  --set infraServices.s3.accessKey="your-s3-access-key" \
  --set infraServices.s3.secretKey="your-s3-secret-key" \
  --set config.jwt.secretKey="your-jwt-secret" \
  --set config.oauth2.google.clientId="your-google-client-id" \
  --set config.oauth2.google.clientSecret="your-google-client-secret" \
  --set config.email.smtp.username="your-smtp-username" \
  --set config.email.smtp.password="your-smtp-password" \
  --namespace iqscaffold-dev-env \
  --create-namespace
```

#### Environment-Specific Deployments

#### Development

```bash
helm upgrade --install user-service ./ \
  --values values-dev.yaml \
  --set infraServices.postgresql.password="${DB_PASSWORD}" \
  --set infraServices.redis.password="${REDIS_PASSWORD}" \
  --set infraServices.rabbitmq.password="${RABBITMQ_PASSWORD}" \
  --set infraServices.s3.accessKey="${S3_ACCESS_KEY}" \
  --set infraServices.s3.secretKey="${S3_SECRET_KEY}" \
  --set config.jwt.secretKey="${JWT_SECRET_KEY}" \
  --set config.oauth2.google.clientId="${GOOGLE_CLIENT_ID}" \
  --set config.oauth2.google.clientSecret="${GOOGLE_CLIENT_SECRET}" \
  --set config.email.smtp.username="${SMTP_USERNAME}" \
  --set config.email.smtp.password="${SMTP_PASSWORD}" \
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
  --set config.oauth2.google.clientId="${GOOGLE_CLIENT_ID}" \
  --set config.oauth2.google.clientSecret="${GOOGLE_CLIENT_SECRET}" \
  --set config.email.smtp.username="${SMTP_USERNAME}" \
  --set config.email.smtp.password="${SMTP_PASSWORD}" \
  --namespace iqscaffold-production-env \
  --create-namespace
```

### Configuration

#### Drone CI Secrets

The following secrets must be configured in Drone CI for automated deployments:

```bash
# Infrastructure Secrets
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_POSTGRESQL_PASSWORD --data "your-postgresql-password"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_REDIS_PASSWORD --data "your-redis-password"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_RABBITMQ_PASSWORD --data "your-rabbitmq-password"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_S3_ACCESS_KEY --data "your-s3-access-key"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_S3_SECRET_KEY --data "your-s3-secret-key"

# Application Secrets
drone secret add --repository IQKV/iqscaffold-user-service --name JWT_SECRET_KEY --data "your-jwt-secret-key"
drone secret add --repository IQKV/iqscaffold-user-service --name GOOGLE_OAUTH2_CLIENT_ID --data "your-google-client-id"
drone secret add --repository IQKV/iqscaffold-user-service --name GOOGLE_OAUTH2_CLIENT_SECRET --data "your-google-client-secret"
drone secret add --repository IQKV/iqscaffold-user-service --name SMTP_USERNAME --data "your-smtp-username"
drone secret add --repository IQKV/iqscaffold-user-service --name SMTP_PASSWORD --data "your-smtp-password"

# Repository and Registry Secrets (already configured)
drone secret add --repository IQKV/iqscaffold-user-service --name HELM_CHARTS_REPOSITORY --data "your-helm-charts-repo-url"
drone secret add --repository IQKV/iqscaffold-user-service --name NEXUS_DEPLOYER_USERNAME --data "your-nexus-username"
drone secret add --repository IQKV/iqscaffold-user-service --name NEXUS_DEPLOYER_PASSWORD --data "your-nexus-password"
```

#### Required Secrets

| Secret                  | Environment Variable          | Required | Description                 |
| ----------------------- | ----------------------------- | -------- | --------------------------- |
| Database Password       | `INFRA_POSTGRESQL_PASSWORD`   | ✅       | PostgreSQL password         |
| Redis Password          | `INFRA_REDIS_PASSWORD`        | ✅       | Redis cache password        |
| RabbitMQ Password       | `INFRA_RABBITMQ_PASSWORD`     | ✅       | Message broker password     |
| S3 Access Key           | `INFRA_S3_ACCESS_KEY`         | ✅       | Object storage access key   |
| S3 Secret Key           | `INFRA_S3_SECRET_KEY`         | ✅       | Object storage secret key   |
| JWT Secret              | `JWT_SECRET_KEY`              | ✅       | JWT signing key (256+ bits) |
| Google OAuth2 Client ID | `GOOGLE_OAUTH2_CLIENT_ID`     | ⚠️       | Google OAuth2 client ID     |
| Google OAuth2 Secret    | `GOOGLE_OAUTH2_CLIENT_SECRET` | ⚠️       | Google OAuth2 client secret |
| SMTP Username           | `SMTP_USERNAME`               | ⚠️       | Email service username      |
| SMTP Password           | `SMTP_PASSWORD`               | ⚠️       | Email service password      |

**Legend:**

- ✅ **Required**: Service will fail to start without this secret
- ⚠️ **Optional**: Feature-specific, service starts but functionality may be limited

#### Environment Variable Mapping

The Helm chart maps Drone CI secrets to application environment variables:

| Drone Secret                  | Helm --set Parameter                | Application Environment Variable              |
| ----------------------------- | ----------------------------------- | --------------------------------------------- |
| `INFRA_POSTGRESQL_PASSWORD`   | `infraServices.postgresql.password` | `IQSCAFFOLD_DATABASE_PASSWORD`                |
| `INFRA_REDIS_PASSWORD`        | `infraServices.redis.password`      | `IQSCAFFOLD_CACHE_REDIS_PASSWORD`             |
| `INFRA_RABBITMQ_PASSWORD`     | `infraServices.rabbitmq.password`   | `IQSCAFFOLD_MESSAGING_RABBITMQ_PASSWORD`      |
| `INFRA_S3_ACCESS_KEY`         | `infraServices.s3.accessKey`        | `IQSCAFFOLD_S3_ACCESS_KEY`                    |
| `INFRA_S3_SECRET_KEY`         | `infraServices.s3.secretKey`        | `IQSCAFFOLD_S3_SECRET_KEY`                    |
| `JWT_SECRET_KEY`              | `config.jwt.secretKey`              | `IQSCAFFOLD_AUTH_JWT_SECRET`                  |
| `GOOGLE_OAUTH2_CLIENT_ID`     | `config.oauth2.google.clientId`     | `IQSCAFFOLD_AUTH_OAUTH2_GOOGLE_CLIENT_ID`     |
| `GOOGLE_OAUTH2_CLIENT_SECRET` | `config.oauth2.google.clientSecret` | `IQSCAFFOLD_AUTH_OAUTH2_GOOGLE_CLIENT_SECRET` |
| `SMTP_USERNAME`               | `config.email.smtp.username`        | `SMTP_USERNAME`                               |
| `SMTP_PASSWORD`               | `config.email.smtp.password`        | `SMTP_PASSWORD`                               |

#### External Services

The service connects to these external infrastructure components:

- **PostgreSQL**: User data storage and authentication
- **Redis**: Session caching, rate limiting, and temporary data
- **RabbitMQ**: Event messaging and inter-service communication
- **S3/MinIO**: Avatar uploads and file storage
- **SMTP Server**: Email notifications and password reset
- **Google OAuth2** (optional): Social authentication integration

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

2. **Redis Connection Issues**

   ```bash
   # Check Redis connectivity
   kubectl exec -it deployment/iqscaffold-user-service -n iqscaffold-dev-env -- \
     redis-cli -h iqscaffold-infra-redis-master.iqscaffold-dev-env.svc.cluster.local ping
   ```

3. **S3/MinIO Storage Issues**

   ```bash
   # Check S3 configuration
   kubectl describe configmap iqscaffold-user-service-config -n iqscaffold-dev-env | grep S3
   ```

4. **Email/SMTP Configuration**

   ```bash
   # Check SMTP settings
   kubectl get secret iqscaffold-user-service-secrets -n iqscaffold-dev-env -o yaml
   ```

5. **OAuth2 Configuration Issues**

   ```bash
   # Verify OAuth2 secrets are set
   kubectl get secret iqscaffold-user-service-secrets -n iqscaffold-dev-env -o jsonpath='{.data.google-client-id}' | base64 -d
   ```

6. **Check Configuration**

   ```bash
   kubectl describe configmap iqscaffold-user-service-config -n iqscaffold-dev-env
   kubectl describe secret iqscaffold-user-service-secrets -n iqscaffold-dev-env
   ```

7. **Test Health Endpoints**
   ```bash
   kubectl port-forward deployment/iqscaffold-user-service 8081:8081 -n iqscaffold-dev-env
   curl http://localhost:8081/actuator/health
   ```

#### Missing Secrets Diagnosis

If deployments fail due to missing secrets, check:

```bash
# List all secrets in namespace
kubectl get secrets -n iqscaffold-dev-env

# Check specific secret content
kubectl get secret iqscaffold-user-service-secrets -n iqscaffold-dev-env -o yaml

# Verify Drone CI secrets are configured
drone secret ls --repository IQKV/iqscaffold-user-service
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
