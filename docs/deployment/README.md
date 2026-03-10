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

<details>
<summary>📋 Pipeline Stages</summary>

The service uses Drone CI/CD pipeline with 10 stages:

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

</details>

<details>
<summary>🔐 Required Drone Secrets</summary>

| Secret Name                       | Purpose                           | Used In                                    |
| --------------------------------- | --------------------------------- | ------------------------------------------ |
| `NEXUS_DEPLOYER_USERNAME`         | Nexus repository authentication   | Artifact publishing, dependency resolution |
| `NEXUS_DEPLOYER_PASSWORD`         | Nexus repository authentication   | Artifact publishing, dependency resolution |
| `SONAR_HOST`                      | SonarQube server URL              | Static code analysis                       |
| `SONAR_TOKEN`                     | SonarQube authentication token    | Static code analysis                       |
| `SLACK_WEBHOOK`                   | Slack notifications webhook URL   | Build status notifications                 |
| `GITHUB_API_ACCESS_TOKEN`         | GitHub API access for releases    | Release creation, changelog generation     |
| `SVC_CONTAINER_REGISTRY_USERNAME` | Container registry authentication | Docker image publishing                    |
| `SVC_CONTAINER_REGISTRY_PASSWORD` | Container registry authentication | Docker image publishing                    |
| `HELM_CHARTS_REPOSITORY`          | Helm charts repository URL        | Kubernetes deployments                     |
| `INFRA_POSTGRESQL_PASSWORD`       | PostgreSQL database password      | Application configuration                  |
| `INFRA_REDIS_PASSWORD`            | Redis cache password              | Application configuration                  |
| `INFRA_RABBITMQ_PASSWORD`         | RabbitMQ message broker password  | Application configuration                  |
| `INFRA_S3_ACCESS_KEY`             | S3 storage access key             | Application configuration                  |
| `INFRA_S3_SECRET_KEY`             | S3 storage secret key             | Application configuration                  |
| `JWT_SECRET_KEY`                  | JWT symmetric signing key (HS256) | Application security                       |
| `GOOGLE_OAUTH2_CLIENT_ID`         | Google OAuth2 client ID           | Authentication integration                 |
| `GOOGLE_OAUTH2_CLIENT_SECRET`     | Google OAuth2 client secret       | Authentication integration                 |
| `SMTP_USERNAME`                   | Email service username            | Email notifications                        |
| `SMTP_PASSWORD`                   | Email service password            | Email notifications                        |

</details>

#### Branch Deployment Strategy

| Branch Type | Auto Deploy | Manual Promote | Target Environment |
| ----------- | ----------- | -------------- | ------------------ |
| `wip`       | ✅ Dev      | -              | Dev                |
| `feature/*` | -           | ✅ Test        | Test               |
| `dev`       | -           | ✅ Staging     | Staging            |
| Tags        | -           | ✅ Production  | Production         |

#### Deployment Commands

The pipeline uses these Helm commands for deployment:

<details>
<summary>Helm Commands</summary>

```bash
# Development (WIP branches)
helm upgrade --install --atomic --wait --timeout 5m iqscaffold-user-service ./ \
  --values ./values.yaml \
  --values ./values-dev.yaml \
  --set image.tag=wip \
  --set infraServices.postgresql.password=${INFRA_POSTGRESQL_PASSWORD} \
  --set infraServices.redis.password=${INFRA_REDIS_PASSWORD} \
  --set infraServices.rabbitmq.password=${INFRA_RABBITMQ_PASSWORD} \
  --set infraServices.objectstorage.accessKey=${INFRA_S3_ACCESS_KEY} \
  --set infraServices.objectstorage.secretKey=${INFRA_MINIO_SECRET_KEY} \
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
  --set infraServices.objectstorage.accessKey=${INFRA_S3_ACCESS_KEY} \
  --set infraServices.objectstorage.secretKey=${INFRA_MINIO_SECRET_KEY} \
  --set config.jwt.secretKey=${JWT_SECRET_KEY} \
  --set config.oauth2.google.clientId=${GOOGLE_OAUTH2_CLIENT_ID} \
  --set config.oauth2.google.clientSecret=${GOOGLE_OAUTH2_CLIENT_SECRET} \
  --set config.email.smtp.username=${SMTP_USERNAME} \
  --set config.email.smtp.password=${SMTP_PASSWORD} \
  --namespace iqscaffold-production-env
```

</details>

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
  --set infraServices.objectstorage.accessKey="your-s3-access-key" \
  --set infraServices.objectstorage.secretKey="your-s3-secret-key" \
  --set config.jwt.secretKey="your-secure-symmetric-key" \
  --set config.oauth2.google.clientId="your-google-client-id" \
  --set config.oauth2.google.clientSecret="your-google-client-secret" \
  --set config.email.smtp.username="your-smtp-username" \
  --set config.email.smtp.password="your-smtp-password" \
  --namespace iqscaffold-dev-env \
  --create-namespace
```

#### Secret Configuration Examples

```bash
# Infrastructure secrets
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_POSTGRESQL_PASSWORD --data "your-postgresql-password"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_REDIS_PASSWORD --data "your-redis-password"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_RABBITMQ_PASSWORD --data "your-rabbitmq-password"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_S3_ACCESS_KEY --data "your-minio-access-key"
drone secret add --repository IQKV/iqscaffold-user-service --name INFRA_MINIO_SECRET_KEY --data "your-minio-secret-key"

# Application secrets
drone secret add --repository IQKV/iqscaffold-user-service --name JWT_SECRET_KEY --data "your-secure-symmetric-key"
drone secret add --repository IQKV/iqscaffold-user-service --name GOOGLE_OAUTH2_CLIENT_ID --data "your-google-client-id"
drone secret add --repository IQKV/iqscaffold-user-service --name GOOGLE_OAUTH2_CLIENT_SECRET --data "your-google-client-secret"
drone secret add --repository IQKV/iqscaffold-user-service --name SMTP_USERNAME --data "your-smtp-username"
drone secret add --repository IQKV/iqscaffold-user-service --name SMTP_PASSWORD --data "your-smtp-password"

```

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
