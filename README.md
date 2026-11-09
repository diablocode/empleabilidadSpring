# Empleabilidad

Empleabilidad is being built as a React client and a Spring Boot API. The API is the only application component that will access Apache Jena Fuseki.

## Local prerequisites

- Java 21
- Node.js 20.19+ or 22.12+
- npm
- Apache Jena Fuseki 6.2.0 with the `empleabilidad` dataset for graph features

## Configure local development

The local defaults are non-secret and allow the backend to start without Google credentials or a running Fuseki service. Copy `client/.env.example` to `client/.env.local` to configure the frontend proxy. The root `.env.example` documents backend environment variables; Spring reads those from the process environment, while the public Google client ID can also be loaded from `client/.env.local`.

For Google sign-in, set `VITE_GOOGLE_CLIENT_ID` in `client/.env.local` to the **public** Google web client ID. When started from the repository root, the backend imports that local file and uses the same ID automatically; setting `GOOGLE_CLIENT_ID` in the backend environment remains supported and takes precedence. To use the local client JSON already present at the repository root without printing or copying its secret, in the frontend PowerShell window run:

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
| `GOOGLE_CLIENT_ID` | `VITE_GOOGLE_CLIENT_ID` from `client/.env.local` | Public GIS client ID; an explicit backend environment value takes precedence |
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

## API contract

Except for health checks, CSRF-token retrieval, Google sign-in, and sign-out, `/api/**` endpoints require the authenticated application session. `POST`, `PUT`, and `DELETE` requests also require the CSRF cookie and matching `X-XSRF-TOKEN` header. JSON errors have the stable shape `{"code":"...","message":"..."}`.

| Method and path | Access | Behavior |
|---|---|---|
| `GET /api/health`, `GET /api/health/fuseki` | Public | Application and graph health |
| `GET /api/auth/csrf` | Public | Creates a session and returns a CSRF token |
| `POST /api/auth/google` | Public + CSRF | Accepts `{"credential":"..."}`; returns the signed-in user's `email`, `name`, and `pictureUrl` |
| `GET /api/auth/me` | Authenticated | Returns the same current-user response |
| `POST /api/auth/logout` | CSRF required; session optional | Ends the current session when present; returns `204` |
| `GET /api/profile` | Authenticated | Reads the user's profile; `404 profile_not_found` if absent |
| `PUT /api/profile`, `DELETE /api/profile` | Authenticated + CSRF | Creates/replaces or deletes only the current user's profile |
| `GET /api/opportunities` | Authenticated | Lists opportunities; supports `offset` (default `0`, max `10000`), `limit` (default `20`, range `1`–`50`), and `currentOnly` (default `false`) |
| `GET /api/opportunities/{offerId}` | Authenticated | Reads an opportunity using the opaque UUID `offerId` from a listing; unknown IDs return `404 opportunity_not_found` |
| `GET /api/recommendations` | Authenticated | Returns up to 20 ranked recommendations for the user's profile |

Opportunity listing returns `offset`, `limit`, `count`, `hasMore`, and `opportunities`. Each opportunity contains an opaque `offerId`, title, available employer/description/location/modality/expiry fields, a freshness state (`CURRENT`, `EXPIRED`, `CLOSED`, `UNKNOWN_STATUS`, or `INDETERMINATE`), and competency requirements with available names, levels, scale, and mandatory flags. The `currentOnly=true` filter includes only `CURRENT` listings. Pagination is zero-based; an offset beyond the available results returns an empty page. The API does not expose RDF IRIs or invent application links.

Validation errors return `400 invalid_request`; missing opportunities return `404 opportunity_not_found`; unavailable graph data returns `503 opportunity_data_unavailable`; malformed graph data returns `502 opportunity_data_invalid`; oversized graph scans return `503 opportunity_data_limit_exceeded`. Pagination and current-only filtering are applied before returning a bounded page. Opportunity API response fields intentionally exclude raw RDF properties and graph identifiers.

Google credential failures return `401 invalid_google_credential`; missing server-side Google configuration returns `503 google_auth_not_configured`. Profile validation returns `400 invalid_profile`; a missing profile returns `404 profile_not_found`; profile graph failures return `503 profile_store_unavailable` or `502 profile_graph_invalid`. Recommendation graph failures use `503 recommendation_data_unavailable` or `502 recommendation_data_invalid`.

## Recommendations API

`GET /api/recommendations` requires an authenticated session and returns at most 20 current opportunities. It returns `READY` with ranked results, `INCOMPLETE_PROFILE` with `missingProfileFields: ["competencies"]` when no saved profile or competency rankings exist, or `NO_MATCHES` when a complete profile has no matching current opportunities. Fuseki failures return `503 recommendation_data_unavailable`; invalid graph data returns `502 recommendation_data_invalid`.

Ranking is deterministic and uses lexicographic coverage criteria, not an unexplained weighted score: (1) the fraction of identified mandatory competency requirements matched, (2) the fraction of mandatory expected levels met where the requirement explicitly declares the same supported 1–5 competency scale as the candidate profile, then (3) the fraction of all identified competency requirements matched. Ties are ordered by offer IRI. Missing competencies are not treated as matches; missing or incompatible scales do not produce level comparisons. Reasons and the returned coverage counts come from these same inputs. Language, education, experience, skills, socioemotional factors, and professional contacts are not currently ranking signals because the offer graph does not provide corresponding comparable requirements. Recommendations are relevance guidance, not eligibility or hiring guarantees. The current local offers are expired, so the endpoint can correctly return `NO_MATCHES` until current offers are added.

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
