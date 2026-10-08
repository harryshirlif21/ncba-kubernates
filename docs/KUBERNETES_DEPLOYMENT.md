# Kubernetes Deployment Guide

## 1. What gets deployed
| File | Resource | Purpose |
|---|---|---|
| `k8s/00-namespace.yaml` | Namespace `country-integration` | Isolation |
| `k8s/01-configmap.yaml` | ConfigMap | Non-secret config (DB URL, SOAP URL, timeouts) |
| *(script-created)* | Secret `country-integration-secret` | DB credentials |
| `k8s/02-mysql.yaml` | Headless Service + StatefulSet (5Gi PVC) | MySQL 8 |
| `k8s/03-deployment.yaml` | Deployment (2 replicas) | The app: probes, limits, non-root, rolling update |
| `k8s/04-service.yaml` | ClusterIP Service | Stable in-cluster endpoint, load balancing |
| `k8s/05-ingress.yaml` | Ingress | External access + rate limit |
| `k8s/06-hpa.yaml` | HPA + PDB | Autoscale 2-10 pods on CPU; keep >=1 pod during node drains |
| `k8s/07-networkpolicy.yaml` | NetworkPolicy | Only the app can reach MySQL |

## 2. Prerequisites
`docker`, `kubectl` pointed at a cluster (`kubectl config current-context`), metrics-server (for the HPA), an ingress controller (optional; port-forward works without it).

Local cluster quick start (pick one):
```bash
minikube start && minikube addons enable ingress && minikube addons enable metrics-server
# or
kind create cluster --name demo
```

## 3. Deploy (one command)
```bash
export DB_USERNAME=appuser
export DB_PASSWORD='<strong password>'
export MYSQL_ROOT_PASSWORD='<another strong password>'

# minikube:  MINIKUBE=true ./scripts/deploy.sh
# kind:      KIND_CLUSTER=demo ./scripts/deploy.sh
# registry:  IMAGE=registry.example.com/team/country-integration-service:1.0.0 PUSH=true ./scripts/deploy.sh
./scripts/deploy.sh
```
The script: builds the image -> loads/pushes it -> applies namespace/config -> creates the Secret -> deploys MySQL and waits -> deploys the app and waits for the rollout -> prints pods/services/HPA.

## 4. Verify
```bash
kubectl -n country-integration get pods            # all Running / READY 1/1
kubectl -n country-integration port-forward svc/country-integration 8080:80 &
curl -s localhost:8080/actuator/health/readiness   # {"status":"UP"}
BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
```
Via Ingress: add the ingress IP to `/etc/hosts` for `country-api.example.com` (or edit the host in `05-ingress.yaml`).

## Running on Docker Desktop Kubernetes (Windows)

### Enable Kubernetes

In Docker Desktop, enable Kubernetes and select the `kubeadm` cluster type so the cluster can use
images built in Docker Desktop's local image store. In PowerShell, select the Docker Desktop context:

```powershell
kubectl config use-context docker-desktop
```

### Deploy from Git Bash

Open Git Bash in the project directory (the one containing `scripts/deploy.sh`) and set the required
credentials in that shell. Replace the placeholders with your own values; do not put real credentials
in tracked files.

```bash
export DB_USERNAME='<database username>'
export DB_PASSWORD='<database password>'
export MYSQL_ROOT_PASSWORD='<MySQL root password>'
./scripts/deploy.sh
```

If Git Bash reports `\r: command not found`, convert the shell scripts to LF line endings and rerun
the deploy command:

```bash
sed -i 's/\r$//' scripts/*.sh
```

### Access the app

Forward the service port from a terminal and leave the command running while testing:

```bash
kubectl -n country-integration port-forward svc/country-integration 8080:80
```

The API is then available at `http://localhost:8080`. If the forwarded pod is deleted, run the
port-forward command again.

On Docker Desktop, the HPA may show `cpu: <unknown>` until metrics-server is installed; this is
expected for that setup. HPA autoscaling was not observed locally. The first request after startup
took about 18 seconds during the local run, consistent with JVM cold start on a local node; warm
requests took 1-3 seconds.

## 5. Operate
```bash
# Scale manually (HPA will override within its min/max)
kubectl -n country-integration scale deploy/country-integration --replicas=4

# Roll out a new version (zero downtime) and roll back if needed
IMAGE=...:1.0.1 ./scripts/deploy.sh
kubectl -n country-integration rollout status deploy/country-integration
kubectl -n country-integration rollout undo deploy/country-integration

# Change config (e.g. SOAP timeout) then restart pods
kubectl -n country-integration edit configmap country-integration-config
kubectl -n country-integration rollout restart deploy/country-integration

# Rotate DB password: update MySQL user first, re-run deploy.sh with the new DB_PASSWORD, rollout restart.
```

## 6. Uninstall
```bash
./scripts/undeploy.sh                 # removes everything incl. data
KEEP_DATA=true ./scripts/undeploy.sh  # keeps the MySQL volume and namespace
```

## 7. Production hardening checklist
- Use a managed MySQL or an operator (HA, backups, PITR) instead of the single-pod StatefulSet.
- Pin images by digest; scan in CI; push to a private registry with an `imagePullSecret`.
- TLS on the ingress (cert-manager), authentication on the API.
- Secrets from Vault / External Secrets / cloud secret manager.
- Move the schema to Flyway migrations and set `DDL_AUTO=validate`.
- Add Prometheus scraping (annotations already present) and the alerts listed in `docs/ARCHITECTURE.md`.
- Set namespace ResourceQuota/LimitRange and review requests/limits after load testing.
