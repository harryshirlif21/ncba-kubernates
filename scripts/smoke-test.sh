#!/usr/bin/env bash
# Usage: BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
j() { curl -s -w '\nHTTP %{http_code}\n' -H 'Content-Type: application/json' "$@"; }

echo "--- health";            curl -s "$BASE_URL/actuator/health"; echo
echo "--- POST kenya";        j -X POST "$BASE_URL/api/v1/countries" -d '{"name":"kenya"}'
echo "--- POST unknown (404)"; j -X POST "$BASE_URL/api/v1/countries" -d '{"name":"Atlantis"}'
echo "--- POST blank (400)";  j -X POST "$BASE_URL/api/v1/countries" -d '{"name":""}'
echo "--- GET all";           j "$BASE_URL/api/v1/countries"
echo "--- GET 1";             j "$BASE_URL/api/v1/countries/1"
echo "--- PUT 1";             j -X PUT "$BASE_URL/api/v1/countries/1" -d '{"capitalCity":"Nairobi City"}'
echo "--- DELETE 1";          j -X DELETE "$BASE_URL/api/v1/countries/1"
echo "--- GET 1 (404)";       j "$BASE_URL/api/v1/countries/1"
