# Stark Group VMS (Vehicle Management System)

A full-stack vehicle registry: **Flutter Web** frontend, **Spring Boot 3.5 / Java 21** REST API, and a **PostgreSQL** database with Flyway migrations. There's no mock data. Every screen reads from and writes to the database.

```
.
├── backend/            Spring Boot REST API
├── frontend/           Flutter Web app
└── docker-compose.yml  PostgreSQL (+ optional containerised backend)
```

## Features

| Requirement | Where it's implemented |
|---|---|
| Vehicle list | Paginated table (cards on narrow screens), search, type/status filters, sorting |
| Add/Register vehicle | Form with client-side and server-side validation |
| Edit vehicle | Loads fresh data from the server and uses optimistic locking to prevent lost updates |
| Delete vehicle | Confirmation dialog; handles a vehicle that someone else already deleted |
| Duplicate vehicle number validation | Live availability check while typing, a service-level check, and a DB unique constraint |
| Real DB-backed data | PostgreSQL. The schema is owned by Flyway, and Hibernate only validates it |

## Running locally

### 1. Database

**TiDB Cloud (MySQL compatible). This is the configured database.**

Copy `backend/.env.example` to `backend/.env` and fill in the host, username and password from the cluster's **Connect** dialog. `.env` is git-ignored. On first start, the backend creates the `vms` database over TLS and Flyway applies the MySQL schema from `db/migration/mysql`.

**Or use PostgreSQL 13+.** Point `DB_URL` at it and Flyway uses `db/migration/postgresql` instead. You can start one with Docker:

```bash
docker compose up -d db
```

Or use an existing local server:

```sql
CREATE ROLE vms LOGIN PASSWORD 'vms';
CREATE DATABASE vms OWNER vms;
```

Flyway creates the tables on the first start.

### 2. Backend (port 8080)

```bash
cd backend
./mvnw spring-boot:run
```

Configure it with environment variables. The defaults are shown:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/vms` |
| `DB_USERNAME` / `DB_PASSWORD` | `vms` / `vms` |
| `SERVER_PORT` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:[*],http://127.0.0.1:[*]` |

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### 3. Frontend

```bash
cd frontend
flutter pub get
flutter run -d chrome                     # uses http://localhost:8080
# or point it at another API:
flutter run -d chrome --dart-define=API_BASE_URL=http://my-host:8080
```

### Tests

```bash
cd backend && ./mvnw test      # 60 tests: unit + full HTTP integration tests (H2 in PostgreSQL mode)
cd frontend && flutter test    # 19 tests: API client, validators, widget tests
```

## REST API

Base path: `/api/v1/vehicles`

| Method | Path | Success | Description |
|---|---|---|---|
| GET | `/` | 200 | List. Query params: `search`, `type`, `status`, `page`, `size` (max 100), `sort` (e.g. `vehicleNumber,asc`) |
| GET | `/{id}` | 200 | Get one vehicle |
| GET | `/availability?vehicleNumber=&excludeId=` | 200 | `{ vehicleNumber, available }`, used for the live duplicate check |
| POST | `/` | 201 + `Location` | Register a vehicle |
| PUT | `/{id}` | 200 | Update a vehicle. Send the `version` you last read to enable conflict detection |
| DELETE | `/{id}` | 204 | Delete a vehicle |

Example request body:

```json
{
  "vehicleNumber": "DHA-METRO-GA-12-3456",
  "make": "Toyota",
  "model": "Corolla",
  "type": "CAR",
  "manufactureYear": 2022,
  "color": "White",
  "ownerName": "Tony Stark",
  "status": "ACTIVE",
  "version": 0
}
```

`type` is one of `CAR, MOTORCYCLE, BUS, TRUCK, VAN, PICKUP, SUV, OTHER`. `status` is one of `ACTIVE, INACTIVE, MAINTENANCE` and defaults to `ACTIVE`. Enum values are case-insensitive.

### Error format

Every error is an RFC 9457 `application/problem+json` body. It carries a stable `code` that clients can branch on and, for validation failures, a per-field `errors` map:

```json
{
  "status": 409,
  "title": "Duplicate vehicle number",
  "detail": "A vehicle with number 'DHA 1234' is already registered",
  "code": "DUPLICATE_VEHICLE_NUMBER",
  "errors": { "vehicleNumber": "is already registered to another vehicle" },
  "instance": "/api/v1/vehicles",
  "timestamp": "2026-11-04T17:40:49Z"
}
```

Codes: `VALIDATION_FAILED` (400), `MALFORMED_REQUEST` (400), `RESOURCE_NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `DUPLICATE_VEHICLE_NUMBER` (409), `VERSION_CONFLICT` (409), `DATA_INTEGRITY_VIOLATION` (409), `UNSUPPORTED_MEDIA_TYPE` (415), `INTERNAL_ERROR` (500). Stack traces and internal messages never reach the client.

## Edge cases handled

**Duplicate vehicle numbers**
- The comparison ignores case, spaces and hyphens: `dha-1234`, `DHA 1234` and `DHA1234` count as the same vehicle. The app stores a `normalized_number` column with a `UNIQUE` constraint for this.
- Race conditions: two simultaneous registrations of the same number both pass the existence check, but the DB constraint catches the second one and the API translates it to a 409. In a test with 20 concurrent requests, 1 succeeded and 19 got 409.
- Editing a vehicle and keeping its own number is not reported as a duplicate. Taking another vehicle's number is.
- The frontend checks availability while the user types (debounced, and it ignores out-of-order responses). The server still re-checks on save, and its error is shown on the field.
- A deleted vehicle's number can be registered again.

**Input validation** (enforced on the server and mirrored on the client)
- All strings are trimmed. A whitespace-only value counts as missing, and blank optional fields are stored as `NULL`.
- Vehicle number: 2–32 characters. Letters, digits, spaces and hyphens only. It must start and end with a letter or digit and contain at least one digit. Unicode scripts work, e.g. Bengali `ঢাকা মেট্রো-গ ১২-৩৪৫৬`, including combining vowel signs. The number is stored upper-cased with whitespace and hyphens tidied.
- Manufacture year must be between 1886 and next year. The upper bound moves with the calendar.
- Field lengths match the DB columns. Unknown enum values, wrong JSON types, malformed JSON and non-JSON content types all return precise 400 or 415 errors.
- A client-supplied `id` in the request body is ignored.

**Concurrency and consistency**
- Optimistic locking (`@Version`): if user B saves over user A's change, B gets a 409 and the UI offers to reload the latest data.
- Editing or deleting a vehicle that someone else already deleted shows a clear message instead of a crash.

**Listing**
- Search is case-insensitive across plate, make, model and owner. It also matches the normalized plate, so `dha1234` finds `DHA-1234`. SQL `LIKE` wildcards (`%`, `_`) in the search are escaped.
- Sorting only accepts whitelisted fields; anything else returns a 400. An `id` tie-breaker keeps paging deterministic.
- Page size is capped at 100. A page past the end returns empty, and the UI steps back automatically when the last item on a page is deleted.
- Non-numeric or negative ids return a 400. Unknown routes return a problem-JSON 404.

**Frontend UX**
- Loading, empty, no-results and error states, each with a retry where it makes sense. If a refresh fails, the previous results stay on screen with a warning banner.
- Network failures and timeouts (15 s) show a friendly message, and non-JSON proxy errors are handled.
- The submit button is disabled while saving to prevent double submits. Deletes in progress can't be triggered twice.
- If the form has unsaved changes, the app asks before leaving it.
- The layout is responsive: a table on wide screens and cards on narrow ones.

## Backend structure

```
com.starkgroup.vms
├── config/       CORS, JPA auditing, Jackson string trimming
├── controller/   REST endpoints (thin, no business logic)
├── dto/          Request/response records (entities are never exposed)
├── entity/       JPA entity + enums
├── exception/    Domain exceptions + global @RestControllerAdvice
├── mapper/       DTO <-> entity mapping
├── repository/   Spring Data JPA repository + Specifications for search
├── service/      Business rules, transactions
├── util/         Vehicle number normalization (single source of truth)
└── validation/   Custom @ValidManufactureYear constraint
```
