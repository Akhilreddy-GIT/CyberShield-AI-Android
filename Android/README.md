# CyberShield AI — Android

Native Android client for the CyberShield AI backend. Kotlin · Jetpack Compose · Material 3 · MVVM · Clean Architecture · Hilt · Retrofit · Room · DataStore · CameraX · OkHttp WebSockets · Coil.

## Open in Android Studio

1. Open the `android/` folder in Android Studio (File → Open).
2. Let Gradle sync. Create `local.properties` with your SDK path if needed:
   ```
   sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```
3. Start the backend on port 8000 (`uvicorn app.main:app --reload --port 8000`).
4. Run the `app` configuration on an emulator (API 26+).

### Backend URL

- Emulator default: `http://10.0.2.2:8000/` (BuildConfig `API_BASE_URL`)
- Physical device: change `API_BASE_URL` / `WS_BASE_URL` in `app/build.gradle.kts` to your PC LAN IP, e.g. `http://192.168.1.10:8000/`

## Features mapped to backend

| Feature | Backend |
|--------|---------|
| AI Expert chat + history | `POST /api/chat`, `GET /api/chat/{case_id}/history` |
| Cases list / detail | `GET /api/cases/by-user/{anon_user_id}`, `GET /api/cases/{case_id}` |
| Risk assessment | `POST /api/cases/assess-risk` |
| Timeline | `POST /api/cases/timeline`, `GET /api/cases/{case_id}/timeline` |
| Evidence upload / list / delete + OCR | `POST /api/evidence/upload`, `GET /api/evidence/{case_id}`, `DELETE /api/evidence/{evidence_id}` |
| Case report JSON + PDF | `GET /api/report/{case_id}`, `GET /api/report/{case_id}/pdf` |
| Optional JWT auth | `POST /api/auth/register`, `POST /api/auth/login` |
| Live case updates | WebSocket `/ws/case/{case_id}` |
| Health | `GET /api/health` |

Anonymous-first: a local `anon_user_id` is stored in DataStore. Login is optional and replaces that ID with the account’s `anon_user_id` from the API.
