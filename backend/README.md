# My Commute API Gateway

Production-boundary migration: **all provider data API keys remain on the server**.
Only Android's separately restricted map SDK key can be client-side, if that SDK
requires it. The Android APK must not contain the transit, walking, POI, Seoul,
TAGO or weather data API secrets.

## Architecture

Android (Firebase email/password login) -> HTTPS Cloud Run -> verify Firebase
ID token -> Firestore per-user quota -> fixed provider endpoint -> native provider
JSON -> existing Android parser.

The gateway returns upstream JSON **only for successful HTTP 200**. It does not
expose server secrets, raw provider error bodies, stack traces or request URLs.

Endpoint | Gateway route | Provider
--- | --- | ---
Place search | GET \`/v1/pois?q=...\` | TMAP POI
Public transit | POST \`/v1/transit/routes\` | TMAP Transit
Walking | POST \`/v1/walking\` | TMAP Pedestrian
Weather | GET \`/v1/weather?base_date=...&base_time=...&nx=...&ny=...\` | KMA short-range
Subway | GET \`/v1/subway?station=...\` | Seoul subway real-time
Bus stops | GET \`/v1/bus/stops?lat=...&lon=...\` | TAGO BusSttnInfo
Bus arrivals | GET \`/v1/bus/arrivals?cityCode=...&nodeId=...\` | TAGO ArvlInfo

## Cloud Run setup (operator actions, not executed in this repository)

1. Use **one Google Cloud project containing Firebase Authentication and
   Firestore**. Configure a Firestore database in Native mode. Enable Cloud Run,
   Cloud Build, Artifact Registry, Firestore and Secret Manager APIs, with a
   billing account and appropriate deployment permissions.
2. Create a dedicated runtime service account and grant it
   \`roles/datastore.user\` and \`roles/secretmanager.secretAccessor\` on the
   relevant secrets only. Keep deployment permissions separate from runtime.
3. Store each real key in Google Secret Manager, separately:
   \`TMAP_APP_KEY\` (POI & walking), \`TMAP_TRANSIT_APP_KEY\`,
   \`KMA_SERVICE_KEY\`, \`SEOUL_SUBWAY_API_KEY\`,
   \`TAGO_BUS_SERVICE_KEY\`. Never commit them or put them in \`.env\`.
4. From the repo root, deploy with your actual values substituted:

\`\`\`bash
gcloud run deploy my-commute-api \
  --project PROJECT_ID --region asia-northeast3 \
  --source backend \
  --service-account commute-gateway@PROJECT_ID.iam.gserviceaccount.com \
  --allow-unauthenticated \
  --set-env-vars FIREBASE_PROJECT_ID=PROJECT_ID,DAILY_UNITS=250,PER_MINUTE=20,TRANSIT_PER_DAY=20,WALKING_PER_DAY=50 \
  --set-secrets TMAP_APP_KEY=TMAP_APP_KEY:latest,TMAP_TRANSIT_APP_KEY=TMAP_TRANSIT_APP_KEY:latest,KMA_SERVICE_KEY=KMA_SERVICE_KEY:latest,SEOUL_SUBWAY_API_KEY=SEOUL_SUBWAY_API_KEY:latest,TAGO_BUS_SERVICE_KEY=TAGO_BUS_SERVICE_KEY:latest
\`\`\`

Cloud Run **public HTTPS ingress** is intentional because a Firebase ID token,
not a Google IAM identity token, authorizes the seven \`/v1\` data endpoints.
Unauthenticated calls must be rejected with HTTP 401 by the gateway. The
\`/healthz\` endpoint is public and contains no provider data.

5. Set \`API_BASE_URL=https://<your-cloud-run-host>\` in local \`local.properties\`
   (do not include any provider key). Keep the existing Firebase Android
   \`google-services.json\`, and rebuild the app.
6. Run a production smoke test: \`/healthz\` returns OK, \`/v1/pois\` without a
   Firebase token returns 401, a signed-in app can search places and routes,
   weather shows forecast data, subway shows statuses without invented ETA,
   TAGO bus stops and arrivals are tested separately. Verify failed upstream
   calls do not reveal secrets. Monitor Cloud Run, Firestore and provider quotas.

## Security and cost constraints

- HTTPS required client-to-server. The server's Seoul provider hop currently uses
  the provider's documented **HTTP** endpoint: this upstream leg is not encrypted.
  The Android app must not contact that host or receive its key. Confirm provider
  policy and HTTPS availability before high-risk production use.
- Firebase ID tokens expire and are obtained using the installed Firebase SDK.
  User authentication alone is **not** proof of a valid subscription or legitimate
  billing account. For a public product, add account abuse controls such as
  Firebase App Check enforcement, onboarding limits and budget alerts.
- Firestore transactions enforce per-UID minute/day quotas across Cloud Run
  instances (weighted transit/walking). The defaults are a **pilot budget**,
  not proven safe quotas for a large public launch. All usage writes cost money.
  The daily counter document has an \`expiresAt\` field; enabling Firestore TTL
  cleanup for this field is an optional operations step.
- This gateway contains no cross-user route cache and stores no location history.
  Coordinates are passed only to the necessary provider during that request.
- Do not expose provider response errors or keys to the UI; Android surfaces
  short statuses. Provider-level approvals, plan limits, API terms, and
  credentials are outside code and must be configured separately.
- No live Cloud Run deployment is performed by this commit; secret provisioning,
  IAM, billing and domain/URL are external actions.
- Android currently targets SDK 28. Current Play Store distribution requirements
  and Android location/background rules must be evaluated before a public store
  release; changing targetSdk requires separate permission and behavior testing.
