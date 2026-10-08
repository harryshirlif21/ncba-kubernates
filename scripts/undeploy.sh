#!/usr/bin/env bash
# Removes everything, INCLUDING the MySQL volume (set KEEP_DATA=true to keep PVCs).
set -euo pipefail
cd "$(dirname "$0")/.."
NS=country-integration
kubectl delete -f k8s/07-networkpolicy.yaml -f k8s/06-hpa.yaml -f k8s/05-ingress.yaml \
  -f k8s/04-service.yaml -f k8s/03-deployment.yaml -f k8s/02-mysql.yaml --ignore-not-found || true
kubectl -n "$NS" delete secret country-integration-secret --ignore-not-found
if [[ "${KEEP_DATA:-false}" != "true" ]]; then
  kubectl -n "$NS" delete pvc -l app=mysql --ignore-not-found
  kubectl delete namespace "$NS" --ignore-not-found
fi
