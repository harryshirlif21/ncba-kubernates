#!/usr/bin/env bash
# Build, (optionally push) and deploy to the current kubectl context.
#   Required env: DB_USERNAME DB_PASSWORD MYSQL_ROOT_PASSWORD
#   Optional env: IMAGE (default country-integration-service:1.0.0), PUSH=true, KIND_CLUSTER, MINIKUBE=true
set -euo pipefail
cd "$(dirname "$0")/.."

: "${DB_USERNAME:?set DB_USERNAME}" "${DB_PASSWORD:?set DB_PASSWORD}" "${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD}"
IMAGE="${IMAGE:-country-integration-service:1.0.0}"
NS=country-integration

echo ">> Building image $IMAGE"
docker build -t "$IMAGE" .

if [[ "${PUSH:-false}" == "true" ]]; then
  echo ">> Pushing $IMAGE"; docker push "$IMAGE"
elif [[ -n "${KIND_CLUSTER:-}" ]]; then
  echo ">> Loading image into kind cluster $KIND_CLUSTER"; kind load docker-image "$IMAGE" --name "$KIND_CLUSTER"
elif [[ "${MINIKUBE:-false}" == "true" ]]; then
  echo ">> Loading image into minikube"; minikube image load "$IMAGE"
fi

echo ">> Applying namespace + config"
kubectl apply -f k8s/00-namespace.yaml -f k8s/01-configmap.yaml

echo ">> Creating/updating secret (not stored in git)"
kubectl -n "$NS" create secret generic country-integration-secret \
  --from-literal=db-username="$DB_USERNAME" \
  --from-literal=db-password="$DB_PASSWORD" \
  --from-literal=mysql-root-password="$MYSQL_ROOT_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -

echo ">> Deploying MySQL"
kubectl apply -f k8s/02-mysql.yaml
kubectl -n "$NS" rollout status statefulset/mysql --timeout=240s

echo ">> Deploying application ($IMAGE)"
sed "s|IMAGE_PLACEHOLDER|$IMAGE|" k8s/03-deployment.yaml | kubectl apply -f -
kubectl apply -f k8s/04-service.yaml -f k8s/06-hpa.yaml -f k8s/07-networkpolicy.yaml
kubectl apply -f k8s/05-ingress.yaml || echo "(ingress skipped: no ingress controller/CRD?)"
kubectl -n "$NS" rollout status deployment/country-integration --timeout=240s

echo ">> Done."
kubectl -n "$NS" get pods,svc,hpa
echo "Try:  kubectl -n $NS port-forward svc/country-integration 8080:80"
