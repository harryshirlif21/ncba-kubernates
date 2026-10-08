# Country Integration Service

**Editor:** Hillary Mwendi Kimaiga

Spring Boot microservice that bridges a **REST** API and the public **SOAP** CountryInfoService,
persists the result in **MySQL**, and exposes **CRUD** endpoints. Ships with Docker, Kubernetes
manifests, deployment scripts and operational guides.

```
Client --POST {"name":"kenya"}--> REST controller --> Service --> SOAP client --> CountryISOCode  (name -> KE)
                                                         |                   \-> FullCountryInfo (KE -> details)
                                                         \--> MySQL (country_info 1--* language)
```

Docs: [Architecture & design decisions](docs/ARCHITECTURE.md) |
[Kubernetes deployment guide](docs/KUBERNETES_DEPLOYMENT.md) |
[Kubernetes troubleshooting guide](docs/TROUBLESHOOTING.md) |
[Architecture and flow diagrams (PDF)](docs/Architecture-Diagrams.pdf)

## Requirements
- Windows with Docker Desktop installed and running (Docker Compose v2).
- Git for Windows.
- Postman Desktop.
- Internet access from the app container to call the public CountryInfo SOAP service.

Java 17+ and Maven 3.9+ are only required when running or testing the app outside Docker. For
Kubernetes, install kubectl and use a cluster (minikube, kind, or cloud).

## Run With Docker Compose

### 1. Clone the repository

Open PowerShell and run:

```powershell
git clone https://github.com/harryshirlif21/ncba-kubernates.git
Set-Location ncba-kubernates
```

Run the remaining commands from the directory containing `docker-compose.yml`. If you cloned the
repository into a different location, change to that directory first.

### 2. Build and start the services

```powershell
docker compose up --build -d
```

This builds the Spring Boot image and starts the app and MySQL in the background. The app is
available at `http://localhost:8080`. MySQL is published on host port `3307` to avoid conflicts
with a MySQL server already installed on Windows; the app connects to MySQL over Docker's network
at `mysql:3306`.

### 3. Confirm both containers are running

```powershell
docker compose ps
```

Wait until MySQL shows `healthy` and the app shows `Up`. To follow live application logs:

```powershell
docker compose logs -f app
```

Press `Ctrl+C` to stop following logs. This does not stop the containers. To stop the services:

```powershell
docker compose down
```

The database volume is retained when using `docker compose down`.

## Verify In Postman Desktop

Use `http://localhost:8080` as the base address. Do not send requests to `/`; the service has no
homepage and its REST routes start with `/api/v1`.

### 1. Check app and database readiness

- Method: `GET`
- URL: `http://localhost:8080/actuator/health/readiness`
- Expected: `200 OK` with `{"status":"UP"}`

This readiness check includes the database connection.

### 2. List saved countries

- Method: `GET`
- URL: `http://localhost:8080/api/v1/countries`
- Expected: `200 OK`. On a fresh database, the list is empty:

```json
{
    "content": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
}
```

### 3. Look up and save a country

- Method: `POST`
- URL: `http://localhost:8080/api/v1/countries`
- In Postman, select **Body** > **raw** > **JSON**, then enter:

```json
{
    "name": "Kenya"
}
```

Postman should send `Content-Type: application/json`. The app calls the public SOAP service and
saves the country details in MySQL. A new record returns `201 Created`; looking up a country already
stored returns `200 OK`. The response contains the country ID, ISO code, name, capital, currency,
flag URL, and languages. The ID can vary. For example:

```json
{
    "id": 1,
    "isoCode": "KE",
    "name": "Kenya",
    "capitalCity": "Nairobi",
    "phoneCode": "254",
    "continentCode": "AF",
    "currencyIsoCode": "KES",
    "countryFlag": "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
    "languages": [
        {
            "isoCode": "swa",
            "name": "Swahili"
        }
    ]
}
```

### 4. Confirm the saved record

Use the `id` from the POST response:

- Method: `GET`
- URL: `http://localhost:8080/api/v1/countries/1`
- Expected: `200 OK` with the saved country details.

You can also send `GET http://localhost:8080/api/v1/countries` again to see it in the country list.

### Troubleshooting

- If readiness is unavailable, run `docker compose ps` and `docker compose logs app` from the project
    directory. MySQL logs are available with `docker compose logs mysql`.
- If the SOAP lookup returns `503 Service Unavailable`, the app is reachable but the external SOAP
    service may be unavailable or unreachable. Check internet access and the app logs, then retry.
- If Postman reports a connection error, confirm Docker Desktop is running and the app container is
    `Up`. Ensure no other process is using host port `8080`.
- Do not change the app's container database URL to `localhost`. The Compose app must connect to
    `mysql:3306`; host tools can connect to the database on `localhost:3307`.

## Test

```powershell
mvn test
```

For an end-to-end test against a running instance, run `scripts/smoke-test.sh` from a shell that
supports it. Set `BASE_URL` to use a different app address.

## Test Status

The following scenarios were verified manually:

| Environment | Manually verified |
|---|---|
| Docker Compose and Postman | POST lookups for Kenya, Tanzania, and Uganda; GET all; GET by ID; GET unknown ID returns 404; PUT partial update; PUT replace languages |
| Docker Desktop Kubernetes | `scripts/deploy.sh` succeeds; two app replicas and MySQL are Running; app recovers after a pod is deleted; scaled from two to four replicas; in-cluster POST lookup returns 201 |

Not yet verified manually:

| Area | Not yet verified |
|---|---|
| REST API | DELETE endpoint; PUT validation error (400) |
| Resilience and scaling | Circuit breaker and stored-data fallback; HPA autoscaling |

## API

| Method | Path | Purpose | Success |
|---|---|---|---|
| POST | `/api/v1/countries` | Body `{"name":"kenya"}`. Sentence-cases the name, calls SOAP `CountryISOCode` then `FullCountryInfo`, stores/refreshes the result | 201 new / 200 existing |
| GET | `/api/v1/countries?page=0&size=20` | List all (paged, max 100) | 200 |
| GET | `/api/v1/countries/{id}` | Get by ID | 200 |
| PUT | `/api/v1/countries/{id}` | Partial update (any of name, capitalCity, phoneCode, continentCode, currencyIsoCode, countryFlag, languages) | 200 |
| DELETE | `/api/v1/countries/{id}` | Delete (languages cascade) | 204 |

```bash
curl -s -X POST localhost:8080/api/v1/countries -H 'Content-Type: application/json' -d '{"name":"kenya"}'
```
```json
{ "id": 1, "isoCode": "KE", "name": "Kenya", "capitalCity": "Nairobi", "phoneCode": "254",
  "continentCode": "AF", "currencyIsoCode": "KES", "countryFlag": "http://www.oorsprong.org/WebSamples.CountryInfo/Flags/Kenya.jpg",
  "languages": [ { "isoCode": "swa", "name": "Swahili" } ] }
```

**Errors** use one shape: `{timestamp, status, error, message, path, correlationId, details[]}`

| Status | When |
|---|---|
| 400 | Blank/invalid name, malformed JSON, bad path parameter |
| 404 | Country unknown to the SOAP service, or ID not stored |
| 503 | SOAP service down/slow/circuit open **and** nothing stored to fall back on |
| 500 | Unexpected (details are logged, never returned) |

Every response carries `X-Correlation-Id` (send your own to trace a request). If the SOAP service is down but the
country was stored before, the stored record is returned with header `X-Data-Source: stored-fallback`.

## Observability
- `/actuator/health/liveness`, `/actuator/health/readiness` (readiness includes the DB)
- `/actuator/prometheus` - JVM, HTTP, Hikari, Resilience4j (`resilience4j_circuitbreaker_*`, `resilience4j_retry_*`) and `country_lookup_total{outcome=success|fallback|failure}`
- Logs: human-readable locally, JSON in the `prod` profile, with `correlationId` on every line

## Project layout
```
controller/  REST layer (MVC "C")        dto/        request/response models
service/     business logic              entity/     JPA models CountryInfo, Language (MVC "M")
soap/        SOAP adapter + parser       repository/ Spring Data JPA
exception/   error model + handler       logging/    correlation-id filter
k8s/         manifests                   scripts/    deploy, undeploy, smoke-test
```

## Known limitations / next steps
- Schema is created by Hibernate (`ddl-auto=update`) for convenience; production should use Flyway/Liquibase with `validate`.
- Caching is per-pod (Caffeine). Use Redis if you need a shared cache across replicas.
- The in-cluster MySQL is a single node for the exercise; use a managed DB/operator for HA and backups.
- Country names are sentence-cased as specified ("South africa"); the SOAP service matches case-insensitively in testing, verify for your inputs.
