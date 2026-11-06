# Empleabilidad

Empleabilidad is being built as a React client and a Spring Boot API. The API is the only application component that will access Apache Jena Fuseki.

## Local prerequisites

- Java 21
- Node.js 20.19+ or 22.12+
- npm
- Apache Jena Fuseki 6.2.0 with the `empleabilidad` dataset for graph features

## Configure local development

The local defaults are non-secret and allow the backend to start without Google credentials or a running Fuseki service. Copy `client/.env.example` to `client/.env.local` to configure the frontend proxy. The root `.env.example` documents backend environment variables; Spring reads these from the process environment, not from a `.env` file.

For Google sign-in, set `GOOGLE_CLIENT_ID` in the backend process and `VITE_GOOGLE_CLIENT_ID` in `client/.env.local` to the same **public** Google web client ID. To use the local client JSON already present at the repository root without printing or copying its secret, in the backend PowerShell window run:

```powershell
$oauthClient = Get-Content .\client_secret_apps.googleusercontent.com.json | ConvertFrom-Json
$env:GOOGLE_CLIENT_ID = $oauthClient.web.client_id
$oauthClient = $null
.\gradlew.bat bootRun
```

In the frontend PowerShell window, configure the same public client ID from the JSON without writing its secret:

```powershell
Set-Location client
$oauthClient = Get-Content ..\client_secret_apps.googleusercontent.com.json | ConvertFrom-Json
"VITE_GOOGLE_CLIENT_ID=$($oauthClient.web.client_id)" | Set-Content .env.local
$oauthClient = $null
npm run dev
```

The client ID is public browser configuration; never put the client secret in Vite configuration or source control. The local client must authorize `http://localhost:5173`. `FRONTEND_ORIGIN` must match the browser origin. Local session/CSRF cookies use `SameSite=Lax`; set `SESSION_COOKIE_SECURE=true` when serving the application over HTTPS.

Backend settings (each has a local default):

| Environment variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Spring Boot HTTP port |
| `FUSEKI_BASE_URL` | `http://localhost:3030` | Fuseki server base URL |
| `FUSEKI_DATASET` | `empleabilidad` | Fuseki dataset name |
| `GOOGLE_CLIENT_ID` | empty | Public GIS client ID; required when sign-in is implemented |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | React origin for browser security configuration |
| `SESSION_COOKIE_SECURE` | `false` | Set `true` for HTTPS deployments |

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
- `GET http://localhost:8080/api/auth/csrf` returns a CSRF token and sets the readable `XSRF-TOKEN` cookie. Send the returned token as `X-XSRF-TOKEN` on authentication/sign-out POST requests.
- `POST http://localhost:8080/api/auth/google` accepts the GIS credential in `{"credential":"..."}`. Google signature, issuer, audience, expiration, and verified email are checked server-side; the raw credential is never stored. A successful response establishes an HTTP-only app session and does not return the stable Google subject.
- `GET http://localhost:8080/api/auth/me` returns the signed-in user's display information; `POST http://localhost:8080/api/auth/logout` invalidates the application session. Other `/api/**` routes require authentication. Profile ownership enforcement is implemented with profile resources in Layer 4.
- To manually verify a real Google sign-in, start both services using the environment setup above, open `http://localhost:5173`, use Google's **Sign in with Google** button, and confirm the signed-in email is shown. Sign out and confirm the app returns to the Google button. No password form is provided.
- Fuseki graph access uses Apache Jena ARQ with a five-second query timeout. Offer reads use bounded pages (maximum 50) and map offers, employers, requirements, candidate competency scales, MCER levels, and recommendation concepts while preserving RDF IRIs. Offers with past end dates or excluded statuses (`cerrada`, `expirada`, `cubierta`) are non-current. Without an end date, only explicitly recognized active statuses (`abierta`, `vigente`, `publicada`, `activa`, `open`, `active`, `published`) are current. If both end date and status are missing, or a status is unrecognized, the listing is not considered current. The live graph has no status literals; broaden the recognized list only when actual source values are confirmed.
- To run the opt-in integration test against the configured live Fuseki dataset, set `$env:FUSEKI_INTEGRATION = "true"` before running `.\gradlew.bat test`. The default test suite uses local RDF fixtures and does not require Fuseki.

## User profile API

The authenticated profile endpoints store each user's candidate profile in the Fuseki default graph:

- `GET /api/profile` returns the signed-in user's profile or `404 profile_not_found` if one has not been created.
- `PUT /api/profile` creates or replaces only the signed-in user's profile.
- `DELETE /api/profile` deletes only the signed-in user's profile and returns `204`.

All three endpoints require the application session. `PUT` and `DELETE` require the CSRF cookie and `X-XSRF-TOKEN` header described above. The profile identifier is a SHA-256-derived opaque IRI from Google's stable subject claim; the raw subject is never stored, accepted from the client, or returned. Profile updates are restricted to that user's candidate and generated child resources, not offers or employers. Fuseki failures return `503`; a malformed stored profile returns `502`.

The JSON profile contains required `name` and `contactEmail`, optional `contactPhone`, and optional lists named `competencies`, `languages`, `education`, `workExperience`, `skills`, `softSkills`, `socioemotionalFactors`, and `professionalContacts`. An entry has a `name`, optional `description`, and optional ISO dates `startDate`/`endDate`. Competency rankings require an existing graph `competencyIri` and a score from 1 to 5; languages require a graph `levelIri` from the MCER concept scheme. Socioemotional factors are descriptive and may include an `evaluationMethod`; no numeric value is stored because the ontology defines no shared scale for those factors. Professional contacts represent real people or groups known professionally, not electronic social networks. Empty optional lists are accepted and do not make a profile invalid.

Example create/update request:

```json
{
  "name": "Example Candidate",
  "contactEmail": "candidate@example.com",
  "contactPhone": null,
  "competencies": [],
  "languages": [],
  "education": [],
  "workExperience": [],
  "skills": [],
  "softSkills": [],
  "socioemotionalFactors": [],
  "professionalContacts": []
}
```

## Verification

Run backend tests and build the frontend:

```powershell
.\gradlew.bat test
Set-Location client
npm ci
npm run build
```

Local-only environment files, node modules, build outputs, Google client JSON files, and files placed under `data/local/` are excluded from version control.
