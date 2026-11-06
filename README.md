# Empleabilidad

Empleabilidad is being built as a React client and a Spring Boot API. The API is the only application component that will access Apache Jena Fuseki.

## Local prerequisites

- Java 21
- Node.js 20.19+ or 22.12+
- npm
- Apache Jena Fuseki 6.2.0 with the `empleabilidad` dataset for graph features

## Configure local development

The local defaults are non-secret and allow the backend to start without Google credentials or a running Fuseki service. Copy `client/.env.example` to `client/.env.local` to configure the frontend proxy. The root `.env.example` documents backend environment variables; Spring reads these from the process environment, not from a `.env` file.

For Google sign-in in Layer 3, set `GOOGLE_CLIENT_ID` in the backend process and `VITE_GOOGLE_CLIENT_ID` in `client/.env.local` to the same **public** Google web client ID. Do not copy the OAuth client JSON or its client secret into the frontend or commit local credential files. `FRONTEND_ORIGIN` must match the browser origin (default `http://localhost:5173`).

Backend settings (each has a local default):

| Environment variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Spring Boot HTTP port |
| `FUSEKI_BASE_URL` | `http://localhost:3030` | Fuseki server base URL |
| `FUSEKI_DATASET` | `empleabilidad` | Fuseki dataset name |
| `GOOGLE_CLIENT_ID` | empty | Public GIS client ID; required when sign-in is implemented |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | React origin for browser security configuration |

Set backend variables in the PowerShell window that starts Spring Boot, for example:

```powershell
$env:SERVER_PORT = "8080"
$env:FUSEKI_BASE_URL = "http://localhost:3030"
$env:FUSEKI_DATASET = "empleabilidad"
$env:FRONTEND_ORIGIN = "http://localhost:5173"
.\gradlew.bat bootRun
```

## Run the application

Start the backend from the repository root:

```powershell
.\gradlew.bat bootRun
```

In another PowerShell window, install and start the React client:

```powershell
Set-Location client
npm ci
npm run dev
```

Open [http://localhost:5173](http://localhost:5173). Vite proxies `/api` requests to `http://localhost:8080` by default; set `VITE_API_PROXY_TARGET` in `client/.env.local` if the backend uses another origin.

## Health checks

- `GET http://localhost:8080/api/health` reports whether the Spring Boot API is available and does not depend on Fuseki.
- `GET http://localhost:8080/api/health/fuseki` runs a read-only SPARQL `ASK {}` query. It returns `200` when the configured dataset responds and `503` when Fuseki cannot be reached.
- Fuseki graph access uses Apache Jena ARQ with a five-second query timeout. Offer reads use bounded pages (maximum 50) and map offers, employers, requirements, candidate competency scales, MCER levels, and recommendation concepts while preserving RDF IRIs. Offers with past end dates or excluded statuses (`cerrada`, `expirada`, `cubierta`) are non-current. Without an end date, only explicitly recognized active statuses (`abierta`, `vigente`, `publicada`, `activa`, `open`, `active`, `published`) are current. If both end date and status are missing, or a status is unrecognized, the listing is not considered current. The live graph has no status literals; broaden the recognized list only when actual source values are confirmed.
- To run the opt-in integration test against the configured live Fuseki dataset, set `$env:FUSEKI_INTEGRATION = "true"` before running `.\gradlew.bat test`. The default test suite uses local RDF fixtures and does not require Fuseki.

## Verification

Run backend tests and build the frontend:

```powershell
.\gradlew.bat test
Set-Location client
npm ci
npm run build
```

Local-only environment files, node modules, build outputs, Google client JSON files, and files placed under `data/local/` are excluded from version control.
