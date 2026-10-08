# Kubernetes Troubleshooting Guide

Set a shortcut first: `alias k='kubectl -n country-integration'`

## 0. 60-second triage
```bash
k get pods -o wide                      # status, restarts, node
k get events --sort-by=.lastTimestamp | tail -20
k describe pod <pod>                    # Events section at the bottom is the most useful part
k logs <pod> --tail=100                 # app logs (JSON in prod)
k logs <pod> --previous                 # logs of the crashed container
k top pods                              # CPU/memory (needs metrics-server)
```

## 1. Pod status -> cause -> fix

| Symptom | Likely cause | Check | Fix |
|---|---|---|---|
| `Pending` | No node capacity; PVC unbound; unschedulable | `k describe pod` (Events: `Insufficient cpu`, `unbound PersistentVolumeClaim`) | Add nodes / lower requests; `k get pvc,storageclass` and fix the storage class |
| `Init:0/1` (stuck) | MySQL not reachable | `k logs <pod> -c wait-for-mysql`; `k get pods -l app=mysql` | Fix MySQL first (section 3) |
| `ImagePullBackOff` / `ErrImagePull` | Wrong image name/tag, private registry, image not loaded into kind/minikube | `k describe pod` | Correct `IMAGE`; add `imagePullSecret`; `kind load docker-image` / `minikube image load` |
| `CrashLoopBackOff` | App fails at startup (DB creds/URL, bad config, OOM) | `k logs <pod> --previous` | See sections 2-3 |
| `OOMKilled` (in `describe`: Last State) | Memory limit too low | `k top pod`; `k describe pod` | Raise `limits.memory`, keep `MaxRAMPercentage` <= 75 |
| `Running` but `0/1 READY` | Readiness failing (usually DB) | `k describe pod` -> probe failures; `curl` the endpoint (below) | Fix DB connectivity; check `/actuator/health/readiness` |
| Restarts climbing | Liveness failing or OOM | `k describe pod` | Increase `startupProbe.failureThreshold` if slow start; fix root cause |
| `CreateContainerConfigError` | Missing Secret/ConfigMap key | `k describe pod` | Re-run `deploy.sh` (creates the Secret) |

Inspect health from inside the cluster:
```bash
k port-forward <pod> 8080:8080
curl -s localhost:8080/actuator/health | jq          # components: db, livenessState, readinessState
curl -s localhost:8080/actuator/prometheus | grep -E 'resilience4j_circuitbreaker_state|country_lookup'
```

## 2. Application errors (by HTTP status)

| Status seen | Meaning | What to do |
|---|---|---|
| 400 | Client sent bad input | Read `details[]` in the response |
| 404 `Country '...' was not found` | SOAP service does not know that name | Try the official English name; verify in SoapUI |
| 404 `Country with id N not found` | No such stored record | `GET /api/v1/countries` |
| 503 | SOAP service down/slow/circuit open and nothing stored | Section 4 |
| 500 | Bug or DB error | Take the `correlationId` from the response and search the logs: `k logs -l app=country-integration --tail=-1 \| grep <id>` |

Follow one request end-to-end: send `-H 'X-Correlation-Id: debug-123'`, then
`k logs -l app=country-integration --tail=-1 | grep debug-123`.

## 3. Database problems
```bash
k get pods -l app=mysql ; k logs statefulset/mysql --tail=50
k get pvc                                        # must be Bound
k exec -it mysql-0 -- mysql -uroot -p            # then: USE countrydb; SHOW TABLES; SELECT COUNT(*) FROM country_info;
```
| Error in app log | Cause | Fix |
|---|---|---|
| `Communications link failure` / `Connection refused` | MySQL not ready, wrong `DB_URL` host | Wait for `mysql-0` Ready; confirm `DB_URL` host is `mysql` and namespace matches |
| `Access denied for user` | Secret does not match the MySQL user (MySQL only creates the user on first init of an empty volume) | Re-create the user in MySQL, or delete the PVC to re-initialise (loses data) |
| `Unknown database` | `MYSQL_DATABASE` mismatch | Create `countrydb` manually or fix the env |
| `Too many connections` | Pods x `DB_POOL_SIZE` > MySQL limit | Lower `DB_POOL_SIZE` or raise `max_connections` |
| Pool timeout (`Connection is not available`) | Pool exhausted / slow queries | Check `hikaricp_connections_*` metrics; scale out; add indexes |

## 4. SOAP / upstream problems
```bash
# Can the cluster reach the SOAP host?
k run curl --rm -it --image=curlimages/curl --restart=Never -- \
  curl -sv -m 10 -H 'Content-Type: text/xml' -H 'SOAPAction: ""' \
  -d '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body><CountryISOCode xmlns="http://www.oorsprong.org/websamples.countryinfo"><sCountryName>Kenya</sCountryName></CountryISOCode></soap:Body></soap:Envelope>' \
  http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso
```
| Symptom | Cause | Fix |
|---|---|---|
| Logs: `SOAP ... unavailable: ... timed out` | Upstream slow | Raise `SOAP_READ_TIMEOUT_MS`; retries/breaker are already active |
| `resilience4j_circuitbreaker_state{state="open"}` = 1 | Breaker open after repeated failures | Wait (20s half-open probes) or fix upstream/egress; responses are served from stored data when possible |
| Curl above fails, DNS error | CoreDNS / egress blocked | `k run -it --rm dns --image=busybox -- nslookup webservices.oorsprong.org`; check NetworkPolicy/egress firewall/proxy |
| `Unparseable SOAP response` | Upstream returned HTML/error page (maintenance, proxy) | Inspect with the curl above |

## 5. Networking / access
```bash
k get svc,endpoints country-integration         # ENDPOINTS must list pod IPs; empty = pods not Ready or selector mismatch
k get ingress ; k describe ingress country-integration
k -n ingress-nginx logs deploy/ingress-nginx-controller --tail=50
```
- 502/503 from ingress: no ready endpoints (see readiness above).
- 404 from ingress: host header does not match the Ingress `host`.
- Port-forward works but ingress does not: ingress controller missing / wrong `ingressClassName`.

## 6. Performance / scaling
```bash
k get hpa                      # TARGETS showing <unknown> => metrics-server missing or no CPU requests set
k top pods
k describe hpa country-integration
```
- High latency: check `http_server_requests_seconds` p95, SOAP timeouts and DB pool metrics; confirm cache hits (repeat requests should not log `SOAP call ...`).
- Not scaling: HPA max reached, or nodes full (pods Pending) - add nodes / cluster autoscaler.

## 7. Rollout problems
```bash
k rollout status deploy/country-integration
k rollout history deploy/country-integration
k rollout undo deploy/country-integration        # back to the previous good version
```
A rollout that hangs usually means the new pods never become Ready (readiness/DB/config); `maxUnavailable: 0` keeps the old pods serving meanwhile.

## 8. Debugging aids
```bash
k exec -it <pod> -- sh                            # read-only FS: only /tmp is writable
k debug -it <pod> --image=busybox --target=app    # ephemeral debug container
k get pod <pod> -o yaml | less                    # effective spec
k get cm country-integration-config -o yaml       # effective config (secrets: k get secret ... -o jsonpath)
```
