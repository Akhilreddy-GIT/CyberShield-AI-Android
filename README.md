# CyberShield AI — Cyber Assistance Expert System

An AI-powered cyber safety and victim assistance application. Not a chatbot —
a domain-locked expert system with legal knowledge retrieval, risk assessment,
guided incident interviews, evidence collection, and case reporting.

This is a v1 build scoped for a college project deadline. See "What's cut from
v1" below for what was deliberately left out and why.

## Quick start

You need two terminals — one for the backend, one for the frontend.

### 1. Backend (FastAPI)

```bash
cd backend
python3 -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env            # then edit .env and add your GROQ_API_KEY
python3 -m uvicorn app.main:app --reload --port 8000
```

Backend runs at `http://localhost:8000`. API docs (auto-generated) at
`http://localhost:8000/docs`.

A `JWT_SECRET` is auto-generated at startup if not set in `.env` — fine for
local demo use. For anything beyond a demo, set a fixed `JWT_SECRET` in
`.env` so tokens survive a server restart.

**You do not need an API key to run this and see it work.** Without
`GROQ_API_KEY` set, the AI Expert chat falls back to knowledge-base template
responses instead of LLM-generated ones — every other feature (guardrails,
risk assessment, evidence vault, case reports, PDF export) works identically
either way. Get a free key at https://console.groq.com if you want live LLM
responses.

### 2. Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at `http://localhost:5173` and talks to the backend at
`localhost:8000` by default. To point it elsewhere, create
`frontend/.env` with:
```
VITE_API_BASE=http://your-backend-url
```

## What this actually does

- **Domain guardrail** — refuses any non-cybercrime topic (Phase 3 of the spec)
- **Dynamic interview** — AI Expert chat asks follow-up questions rather than
  answering blind, orchestrated through LangChain (`ChatGroq` + prompt
  templates + message history)
- **Vector-based RAG** — legal knowledge retrieval uses a real vector
  database (ChromaDB) with cosine-similarity search over TF-IDF embeddings,
  not keyword substring matching. It correctly surfaces relevant law even
  when the user's phrasing shares no literal words with the knowledge base
  entry (e.g. "he keeps texting me after I said stop" correctly retrieves
  the stalking provision without the word "stalking" appearing anywhere).
- **Real OCR** — screenshots uploaded as evidence are run through Tesseract
  and the extracted text is stored and shown in the Evidence Vault, so
  evidence becomes reviewable/searchable, not just an opaque file.
- **JWT authentication, anonymous-first preserved** — optional accounts
  (`/api/auth/register`, `/api/auth/login`) let someone's case history
  follow them across devices. Every existing endpoint still works with
  zero authentication — accounts are additive, not a requirement, per
  Phase 9 of the spec.
- **Real-time case updates via WebSockets** — `/ws/case/{case_id}` pushes
  a live update the instant a case's risk level or status changes (e.g.
  critical escalation in chat, or a risk assessment being run), no polling.
- **Risk assessment** — rule-based, explainable scoring (Low/Medium/High/Critical)
- **Emergency escalation** — critical-risk messages immediately surface
  helpline numbers in the chat UI
- **Evidence vault** — file upload, storage, OCR, and per-case organization
- **Case report generation** — structured report viewable in-app or
  downloadable as PDF
- **Anonymous-first** — no accounts required; a random local session ID
  tracks your cases on your device only, and remains fully functional
  whether or not you create an account

## What's still cut, and why (honest scope notes)

A larger feature list was requested partway through the build — this
section explains what was and wasn't feasible to build for real in the
time available, so nothing here is presented as more finished than it is.

**Built for real in this pass:** OCR, vector-DB RAG, JWT auth (anonymous
preserved), WebSocket live updates. All four are genuinely functional —
tested end-to-end, not stubs.

**Still out of scope, deliberately:**

| Cut | Why |
|---|---|
| Image forgery detection | A real research problem (ELA, sensor-noise analysis, deepfake detection) — not something that can be done honestly as a quick add. A fake version would be actively misleading in a tool meant to support real evidence. |
| AI-generated incident timeline from chats | Needs careful prompt design and evaluation to avoid hallucinating events that didn't happen — too risky to rush for a system whose output may inform legal action. |
| Voice complaint recording + speech-to-text | Needs a speech model (e.g. Whisper) which is a meaningful new dependency and testing surface; better done as a focused follow-up than bolted on under time pressure. |
| Streaming LLM responses | Cosmetic improvement to chat UX; lower priority than the four features that add real capability. |
| PostgreSQL | SQLite is genuinely fine at this scale (single demo deployment); migrating adds deployment complexity without changing what the app can do. |
| Docker + CI/CD | Deployment tooling, not application functionality — valuable for a real production rollout, not for what's being demonstrated here. |
| Admin/law-enforcement analytics dashboard | Needs a second user role and permission model that doesn't exist yet; building it shallow would be worse than not having it. |

If you need any of these for a specific requirement (e.g. your college
explicitly grades on Docker), say so and it can be prioritized next —
better to be told which ones actually matter than to guess.


## Important: legal content disclaimer

The legal knowledge base (`legal_kb.py`) summarizes publicly known provisions
of India's IT Act 2000 and Bharatiya Nyaya Sanhita 2023. **This has not been
reviewed by a lawyer.** Every entry includes a `source_note` field so you can
see exactly what it's based on. Before treating this as anything beyond an
academic demo, have the legal content verified by a qualified professional —
this matters more than any other part of the project, since it's advising
people who may be actual victims.

## Project structure

```
backend/
  app/
    main.py                 # FastAPI app entrypoint
    routers/                # chat, cases, evidence, report, auth, ws_case
    services/                # intent classifier, risk engine, LLM agent,
                             # OCR, vector RAG, JWT auth
    knowledge_base/          # curated legal reference data
    models/                  # SQLAlchemy models + Pydantic schemas
  requirements.txt
  .env.example
  chroma_store/             # vector DB persistence (created on first run)

frontend/
  src/
    pages/                  # Home, Chat, ReportIncident, Evidence, Legal,
                             # Emergency, Awareness, Cases, Account
    components/              # Shell (nav), shared UI (Card, Button, RiskBadge)
    api/client.js             # backend API wrapper incl. auth + WebSocket
```

## Known limitations (be upfront about these in your viva)

- Intent classification is keyword-based, not ML-based — fast and
  transparent, but will miss unusual phrasings. This is a deliberate
  precision-over-recall tradeoff for a v1.
- Risk scoring is rule-based and explainable rather than learned — this is
  intentional for a safety-critical feature (see `risk_engine.py` comments),
  not a shortcut.
- SQLite is not suitable for concurrent multi-user production load — fine
  for a demo/single-reviewer setup.
- No real authentication — anyone with a case ID can view that case. Fine
  for an anonymous-first demo; would need real access control before any
  actual deployment.
