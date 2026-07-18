# CyberShield AI Backend — Upgrade Notes

All existing API contracts, endpoint URLs, auth, routers, and DB models are
preserved. The Android app needs no changes. Below is what changed and why.

## Iteration 4 — Production polish pass: backend-driven recovery lifecycle, risk de-escalation bug fix

Full audit of the message → intent → risk → case → timeline → recovery →
persistence → API → Android flow, per the "final production polish" brief.
Verified live via uvicorn (fresh SQLite DB each run) — see "Verified" below.

**Starting finding: the specific fabricated values named in the brief
("97% Complete", "85% Complete", "Protected" on a brand-new case) do not
exist anywhere in this backend's code.** `grep`ed the entire `app/` tree for
"progress", "Protected", "% Complete" and any percentage-style literal —
none found. `CaseOut` (the only case-detail response model) has never had a
recovery-progress or protection-status field at all; it only ever returned
`id, category, risk_level, risk_score, status, created_at`. So the
fabricated numbers described can't be coming from this backend's current
API responses.

**Root cause, once traced end-to-end:** there was *nothing real* for a
recovery percentage or lifecycle label to bind to. `case.status` only ever
had two real transitions in the whole codebase (`open` → `escalated`, plus
a dead, never-set `under_review` value and no way to reach `closed`). If
an Android client needs to show "recovery progress" and the backend gives
it no such field, the two ways that plays out are either (a) the screen
just doesn't show anything, or (b) something upstream/client-side ends up
inventing a number to fill the gap. Either way, the actual bug to fix on
this side of the fence is the *absence* of a truthful, backend-computed
lifecycle — which is what this iteration adds.

**1. New `app/services/recovery_lifecycle_service.py` — backend-driven
recovery lifecycle (fixes issues #1, #2, #3 in the brief).**
Introduces the 8-stage lifecycle requested in the brief (`open` →
`immediate_action_required` → `in_progress` → `evidence_uploaded` →
`authorities_contacted` → `recovery_ongoing` → `resolved` → `closed`),
computed *only* from real, already-persisted state:
  - `open` / 0%: a case that was just created and has none of the below yet.
    **A brand-new case is now always, provably, `open`/0% — never any
    other value, by construction, since every later stage requires a real
    row/column to already exist.**
  - `immediate_action_required`: risk has escalated to High/Critical
    (reuses `risk_engine.py`'s existing `risk_rank` comparison — no new
    risk logic).
  - `in_progress`: 2+ real user messages exist on the case.
  - `evidence_uploaded`: at least one `Evidence` row exists.
  - `authorities_contacted`: the timeline (already built by
    `timeline_service.py`, itself keyword/pattern-driven off real messages)
    contains a "bank/platform contacted", "card blocked", or "complaint/FIR
    filed" entry.
  - `recovery_ongoing`: both evidence uploaded *and* authorities contacted.
  - `resolved` / `closed`: **only** ever set by an explicit action — see
    new endpoints below — never inferred from text, since "this case is
    actually resolved" is a real-world judgment call, not a pattern match.
`recovery_progress_percent` is simply the stage's position in the ordered
list (0, 14, 29, 43, 57, 71, 86, 100) — never an independently invented
number.

**2. `app/models/db.py` — added `Case.recovery_stage_override` column.**
Nullable `"resolved" | "closed" | None`. This is the *only* place a
terminal lifecycle stage is stored, and it is only ever written by the two
new explicit endpoints below. A new incoming chat message never resets or
advances it — verified live (see below): resolving a case and then sending
it a new message leaves it `resolved`, exactly as intended.

**3. `app/routers/cases.py` — new `POST /api/cases/{id}/resolve` and
`POST /api/cases/{id}/close` endpoints.** Additive only; no existing
endpoint signature changed. Each sets `recovery_stage_override` and
broadcasts the update over the existing case websocket.

**4. `app/models/schemas.py` — additive fields on `CaseOut` and
`ChatMessageOut`.** `recovery_stage`, `recovery_stage_label`,
`recovery_progress_percent` (case endpoints); `recovery_stage`,
`recovery_progress_percent` (chat endpoint, `None` when a turn produced no
case — greetings/educational Q&A — rather than a fabricated default).
Every existing field is untouched and still present, so any Android build
that only reads the old fields keeps working unmodified.

**5. Real bug found and fixed — silent risk de-escalation in the manual
`POST /api/cases/assess-risk` form (fixes issue #7, "Risk Consistency").**
Reproduced live: a case that chat's auto-assessment had already escalated
to `Critical Emergency` (score 30, from "shared my OTP" + "money
deducted" language) got silently dropped to `Medium Risk` (score 4) the
moment the manual risk-assessment form was submitted with a different,
weaker subset of ticked factors — because the endpoint unconditionally
overwrote `case.risk_level`/`risk_score` with whatever the form alone
produced. `risk_engine.py`'s own module docstring already states the
product design: risk is escalate-only, de-escalation must be a deliberate
action, never an automatic side effect of a different signal source. The
manual form was violating its own codebase's documented rule. Fixed by
reusing the same `risk_rank()` comparison `chat.py` already uses for its
own escalation checks — the form can still raise a case's risk, and still
reports back exactly what *its own* factors imply, but it can no longer
silently pull the persisted, single-source-of-truth risk level backward.

**6. Evidence upload (`app/routers/evidence.py`) now returns and
broadcasts the case's recovery stage.** Additive fields
(`recovery_stage`, `recovery_progress_percent`) alongside the existing
response; existing fields unchanged.

**What was checked and found already correct (no changes needed):**
- Educational questions and greetings: already gated by
  `intent_classifier.py` before any case/DB write — verified live, `hi`,
  `thanks`, "What is ransomware?", "How does phishing work?" all return
  `case_id: null` and create nothing.
- Timeline: already deduped, already only fires on real incident-pattern
  matches (`timeline_service.py`), verified no duplicate entries on
  repeated identical messages.
- Risk single-source-of-truth (chat path): `risk_engine.py` already
  centralizes scoring; `chat.py` already does the same escalate-only
  comparison the assess-risk form was missing (see #5).
- Category consistency: `category` is one column, read identically by
  chat, cases, case-details, and the PDF report — verified live that a
  case's category is identical across `POST /api/chat` and
  `GET /api/cases/{id}` for the same case.
- Context-aware helplines: verified live for financial fraud (1930 +
  portal), sextortion/blackmail (1930, portal, 112, 181), and
  minor-involved cases (adds Childline 1098 on top of the base set) —
  all match the brief's examples exactly.
- Recovery plans / playbooks: `playbooks.py` is static, curated,
  non-LLM-generated reference content — no hallucination surface.
- `agent_service.py`'s LLM system prompt already explicitly forbids
  inventing legal sections, helplines, or authorities, and the risk value
  shown to the LLM is locked to the one already computed server-side.

## Verified (live server testing, not just static review)

Booted with `uvicorn`, fresh SQLite DB, no `GROQ_API_KEY` (template-fallback
path — the harder path to get right, since nothing is LLM-smoothed):
- Greeting (`"hi"`) → `case_id: null`, `recovery_stage: null`. No case
  created.
- Educational question (`"What is ransomware?"`) → substantive templated
  answer, `case_id: null`. No case created.
- Low-risk real incident (cyberbullying) → new case, `recovery_stage:
  "open"`, `recovery_progress_percent: 0`. Exactly matches the brief's
  requirement.
- Second real message on that case → `in_progress` / 29%.
- Evidence upload on that case → `evidence_uploaded` / 43%.
- Message logging a filed complaint → timeline entry created,
  `recovery_ongoing` / 71% (both evidence + authorities-contacted true).
- `POST .../resolve` → `resolved` / 86%; a subsequent new chat message on
  the same case does **not** revert it — still `resolved` / 86%.
- `POST .../close` → `closed` / 100%, `status: "closed"`.
- Critical incident (OTP shared + money lost) → case auto-escalates to
  `Critical Emergency` (score 30) on the first message,
  `immediate_action_required` / 14%.
- Manual `assess-risk` form submitted afterward with a weaker factor set →
  before fix: case silently dropped to `Medium Risk`/4. After fix: case
  correctly stays at `Critical Emergency`/30 (form's own response still
  honestly reports what its own factors alone produced).
- `GET /api/cases/by-user/{id}` includes the new recovery fields
  consistently with `GET /api/cases/{id}`.
- `GET /api/cases/{nonexistent}` → still `404 Case not found`, unchanged.
- All existing routes (`/api/chat`, `/api/cases/*`, `/api/evidence/*`,
  `/api/report/*`, `/ws/case/{id}`) still registered and reachable —
  confirmed via FastAPI's route table, no import errors anywhere in the
  package.

## Confirmations requested in the brief

- **Every modified/added file:** `app/services/recovery_lifecycle_service.py`
  (new), `app/models/db.py`, `app/models/schemas.py`,
  `app/routers/cases.py`, `app/routers/chat.py`, `app/routers/evidence.py`.
- **No fabricated values remain anywhere:** confirmed by construction — a
  new case is `open`/0% because every later stage requires a real
  persisted row/column; `resolved`/`closed` are never text-inferred, only
  ever set by an explicit endpoint call.
- **Recovery progress is fully backend-driven:** yes — computed in one
  place (`recovery_lifecycle_service.py`), consumed identically by
  `GET /api/cases/{id}`, `GET /api/cases/by-user/{id}`,
  `POST /api/chat`, and evidence upload.
- **Every API response is deterministic and derived from real backend
  state only:** yes for everything touched this iteration; the pre-existing
  risk/timeline/category/helpline paths were independently verified
  live and already met this bar.
- **Android compatibility:** every change this iteration is additive
  (new optional response fields, two new endpoints). No existing endpoint
  URL, request schema, or existing response field was removed, renamed, or
  had its meaning changed. An unmodified Android build continues to work
  exactly as before; it simply won't display the new lifecycle fields
  until it's updated to read them.

## Iteration 3 — Small-talk gate fix, GVP contact fields, Call/Notification Guardian accuracy, validation hardening

Verified live via uvicorn across every existing feature area: auth, chat,
cases, manual + auto risk assessment, timeline, evidence upload, report/PDF,
call guardian, notification guardian, crisis assessment, institution
routing, correlation, by-user case lookup. No regressions found.

**1. Fixed the small-talk / intent-gate bug (highest priority).** A
previous iteration already built the domain guardrail (`REFUSAL_MESSAGE`,
`classify_intent`), but a message like "hi", "thanks", "okay bye", or an
empty/emoji-only message fell through the classifier's ambiguous branch as
`OTHER_CYBER` with `is_cyber_related=True` — which meant `chat.py` created
a real `Case` row (with risk scoring, category assignment, and — in the
no-LLM template fallback — a **fabricated-looking legal citation**, e.g.
"hi" was answered with IT Act §67 "Publishing Obscene Material"). Verified
this live before fixing it: a fresh DB had a real case row after sending
just "hi".

Fixed with two changes:
- `intent_classifier.py`: added `_is_pure_small_talk()` (a conservative,
  whole-message regex covering greetings, thanks, farewells, "ok"/"yes"/
  "no", "who made you", "tell me a joke", etc. — including multi-token
  combinations like "okay bye") and `_has_no_classifiable_content()` (empty
  / whitespace / emoji-or-punctuation-only). Both are checked **before**
  the ambiguous fallback and **after** the cyber-keyword scan, so a
  message that mixes a greeting with a real report — "hi, someone is
  blackmailing me" — still correctly routes to the cyber path; only pure
  small talk is redirected. Added `NO_CONTENT_MESSAGE` as a distinct,
  gentler prompt for the empty/emoji case vs. the off-topic refusal.
- `chat.py`: the domain-guardrail check now runs **before** any `Case` row
  is created (previously the case was created first, then the refusal
  checked afterward — so even genuinely off-topic messages were creating
  cases). A brand-new case is never created just to hold a refused
  exchange; if the caller passed an existing `case_id`, the refusal is
  still appended to that case's history for natural conversation flow.
- `schemas.py`: `ChatMessageOut.case_id` changed from required `str` to
  `Optional[str] = None`, since a pure small-talk/off-topic turn with no
  prior case now correctly returns no case id. Additive/backward-safe —
  existing clients that always expected a string will simply see `null`
  in this one new scenario, which is the correct signal.

Verified against every literal example from the spec (`hi`, `hello`,
`hey`, `good morning`, `how are you`, `what is your name`, `tell me a
joke`, `who created you`, `thank you`, `nice`, `okay`, `bye`, `random
chatting`) plus edge cases (empty string, whitespace-only, emoji-only,
"okay bye") — none create a case, none call the LLM, none touch the
database beyond an optional history append.

**2. GVP institution profile — field names now match the real committee
list.** `institution_profiles.py`'s `InstitutionProfile` previously had
generic/approximated field names (`campus_security`, `emergency_faculty`,
`student_welfare` doubling as "Dean of Students"). Renamed to the actual
GVP structure requested: `emergency_head`, `womens_cell`,
`anti_ragging_committee`, `security_office`, `dean`, `principal`,
`student_welfare`, `college_counselling`, `local_police`. The class stays
fully generic (`contact_points()` is a plain ordered list any institution
profile can populate a subset of), so adding a second institution later is
still just appending an `INSTITUTION_PROFILES` entry — no routing code
changes. Contact values are still intentionally left empty for GVP; add
real numbers directly to each `ContactPoint(..., contact="...")` in
`app/knowledge_base/institution_profiles.py` — the routing engine
auto-includes any contact as soon as it's non-empty, and continues to
gracefully omit unconfigured ones (verified: `/api/crisis/assess` with
`institution_id=gvp` correctly returns GVP first with a "not yet
configured" note, falling back to government helplines in the meantime).

**3. Call Guardian & Notification Guardian — broader, more accurate
detection.** Widened the existing regex detectors in
`call_guardian_service.py` and `notification_guardian_service.py` to catch
more of how victims and real scam messages actually phrase things —
looser word order, more Indian-English/Hinglish variants (bank names,
UPI apps, "digital arrest" video-call scam phrasing, SIM "upgrade to 5G"
social engineering, courier company names, terser job-scam phrasing like
"asked for a registration fee upfront" instead of requiring the exact
"job offer...fee" structure), while keeping every detector a plain,
auditable regex (no black-box scoring). Deliberately avoided adding
over-broad triggers — e.g. rejected a "gpay|phonepe|paytm" bare-mention
pattern during testing because it would have flagged completely benign
messages ("I paid rent via GPay") as UPI Scam; kept only app-name mentions
that co-occur with actual scam context (collect request, refund, verify,
pin). Verified against both a detection test set (bank impersonation, UPI
scam, digital-arrest police impersonation, SIM swap, job scam, fake KYC,
lottery scam, courier scam) and a benign test set (family check-in call,
delivery notification, legitimate bank statement, casual friend chat) —
zero false positives introduced, several previously-missed real patterns
now correctly caught (e.g. "offered a work from home job and asked for a
registration fee upfront" now correctly flags as Job Scam; previously
scored 0).

**4. Validation hardening.** `ChatMessageIn.message` now has a
`max_length=8000` constraint (Pydantic), so an oversized/spam payload gets
a clean `422` before touching regex/LLM pipelines, instead of being
processed. Combined with fix #1 above, empty, whitespace-only, and
emoji-only messages are now also handled gracefully with no case creation
and no fabricated response content.

## Iteration 2 — Evidence Intelligence, AI Evidence Analyzer, Incident Timeline Engine

Verified in this session via a live local server (uvicorn), not just read
through: created cases via `/api/chat`, uploaded a real image through
`/api/evidence/upload`, and inspected `/api/cases/{id}/timeline` and
`/api/evidence/{id}` responses directly.

**1. Evidence Intelligence Engine** (`app/services/evidence_intelligence_service.py`)
Extracts phone numbers, emails, URLs, domains, UPI IDs, bank names,
transaction IDs, QR content (if provided), dates, times, and social media
handles from OCR'd evidence text, reusing `case_memory_service.extract_entities`
for the fields it already covers rather than duplicating regex. Produces a
plain-language summary and flags known scam/urgency phrases. Verified
against clean text: correctly pulled all entity types from a synthetic
phishing message. Verified through the real OCR pipeline too — Tesseract's
accuracy on a low-quality synthetic screenshot degraded some values (a
digit misread, a domain suffix mangled), which is Tesseract's documented
limitation, not a bug in the extraction logic itself; noting this honestly
rather than omitting it.

**2. AI Evidence Analyzer** (`app/services/evidence_analyzer_service.py`)
Two-layer analysis of evidence text: a rule-based detector (always
available, zero cost, every flag traceable to a matched phrase) covering
urgency manipulation, fake authority, money requests, OTP/credential
harvesting, prize/lottery bait, investment bait, impersonation, threats,
and phishing links — plus a message-type classifier (banking notification,
OTP phishing, fake investment, blackmail, cyberbullying, fake customer
care, job scam, prize scam, impersonation chat). An optional LLM narrative
layer runs on top when GROQ_API_KEY is set, grounded in the rule-based
findings so it can't contradict them; falls back to a deterministic
template otherwise. Verified: correctly classified a synthetic phishing
message as `otp_credential_phishing` with `threat_level: high` and
`requests_otp_or_credentials: true`.

**3. Incident Timeline Engine** (`app/services/timeline_service.py`)
Detects incident-lifecycle actions (message received → link clicked → QR
scanned → credentials/OTP/PIN shared → app installed → money transferred →
account locked → bank contacted → card blocked → complaint filed) in both
chat messages and evidence text, and auto-writes `TimelineEvent` rows via
the pre-existing `TimelineEvent` model and `/api/cases/{id}/timeline`
endpoint (that endpoint already existed but was never auto-populated
before this iteration — only reachable via the manual POST). Dedupes
against already-recorded stages so re-mentioning a fact doesn't create
duplicate entries. Verified across two chat turns: the first message
correctly logged 5 ordered stages, a follow-up correctly skipped the
already-logged OTP-shared stage and appended only the 2 genuinely new
ones. Also verified from an evidence upload's description text, tagged
with its source filename. New events broadcast over the existing
`/ws/case/{case_id}` websocket as an additive `timeline_events_added` field.

**Integration notes:**
- `evidence.py`'s upload response gained two new additive fields
  (`intelligence`, `scam_analysis`); all previously existing fields are
  unchanged. Added one new optional form field (`qr_content`, defaults to
  `""`) — existing Android clients that don't send it are unaffected.
- Evidence-derived entities are merged into the same `Case.facts_json`
  used by chat, so the agent also knows about facts found in a screenshot,
  not just ones typed in chat.
- `case_memory_service.py`: `_BANK_NAMES`/`_PLATFORM_NAMES` were exposed as
  public `BANK_NAMES`/`PLATFORM_NAMES` (old private names kept as aliases)
  so the new evidence module could reuse them instead of duplicating the
  list — no behavior change.

## New files

- **`app/services/case_memory_service.py`** — extracts structured facts
  (amount lost, bank, platform, timeline, phone numbers, emails, UPI IDs,
  transaction IDs, URLs) from every message via regex and persists them per
  case. Fed into the LLM prompt as "KNOWN CASE FACTS" so the agent never
  re-asks something the user already told it.

- **`app/knowledge_base/playbooks.py`** — 18 specialized incident playbooks
  (UPI fraud, QR scam, investment scam, job scam, fake loan app, SIM swap,
  Instagram/WhatsApp/email account hacks, sextortion, cyberbullying,
  ransomware, malware, child online safety, senior citizen scams, deepfakes)
  with curated Immediate Actions / Next 24 Hours / Evidence to Preserve /
  Prevention. Matched from message text and injected into the prompt as
  authoritative reference material — used by both the LLM path and the
  no-LLM template fallback.

## Modified files

- **`app/models/db.py`** — added `Case.facts_json` (nullable `Text`) for
  persisted case memory. `init_db()` now also runs a minimal in-place
  `ALTER TABLE` migration so existing production SQLite databases pick up
  the new column without needing a fresh DB. Tested against both a fresh
  DB and a simulated pre-existing old-schema DB.

- **`app/services/agent_service.py`** — system prompt rewritten to enforce
  the exact structured response format (Situation Summary → Risk Level →
  Immediate Actions → Next 24 Hours → Evidence to Preserve → Authorities →
  Prevention → Follow-up Question), stricter single-question adaptive
  intake, and explicit instruction to use known case facts instead of
  re-asking. `generate_response()` gained an optional `facts_json` param
  (defaults to `None`, so old callers still work) and now also injects the
  matched playbook into the prompt. The no-LLM template fallback
  (`_template_response`) now also uses the matched playbook for more
  specific Immediate Actions / Next 24 Hours / Evidence sections instead of
  one generic 3-line list for every case.

- **`app/services/risk_engine.py`** — **redesigned signal matching from
  rigid keyword substrings to flexible regex detectors.** This fixes a real
  bug: messages like *"I ended up sharing my OTP"* or *"Rs 45000 got
  deducted"* scored **0** under the old exact-phrase lists (only "shared my
  otp" / "money was deducted" matched), silently returning LOW risk for a
  genuinely critical case. The new detectors are verb/tense-flexible regex
  (still fully auditable — every detector is a plain inspectable pattern,
  not a black-box model) and also added amount-aware scoring (a financial
  loss ≥ ₹10,000 now scores higher than an unspecified/small loss).
  `assess_risk()` (the manual form endpoint) is unchanged.

- **`app/services/intent_classifier.py`** — same regex-flexibility fix
  applied to the emergency-detection markers (self-harm, imminent danger,
  child safety, active exploitation, account-takeover-urgent). Previously
  *"someone is threatening to leak my private photos"* did not trip
  `is_critical` because it didn't exactly match the literal phrase "leaked
  my photos" — now it does.

- **`app/routers/chat.py`** — now calls `update_case_facts()` before
  generating a response and passes `facts_json` through. Also fixes a real
  bug where case `status` only escalated to `"escalated"` when the
  *intent classifier's* narrow `is_critical` flag fired — a case could reach
  `risk_level = "Critical Emergency"` via the scored engine alone (e.g.
  large financial loss + OTP shared, without literally matching an
  emergency phrase) and stay stuck at `status: "open"`. Status now also
  escalates whenever `risk_level` reaches Critical, and helplines are
  surfaced in that case too.

## Verified (live server testing, not just unit tests)

- Fresh-DB boot and old-schema-DB migration both tested directly.
- Full chat flow tested across: UPI fraud + OTP sharing (now correctly
  reaches Critical), sextortion/blackmail (now correctly flags critical +
  matches playbook), WhatsApp/Instagram hacking, investment scam,
  cyberbullying, ransomware, malware/remote-access scam, job/loan scam,
  self-harm language, and off-topic refusal.
- Cases, timeline, manual risk-assessment form, chat history, auth
  register/login endpoints all confirmed working unchanged.
- No-LLM template fallback path exercised end-to-end (this sandbox's
  network policy doesn't allow outbound calls to api.groq.com, which
  incidentally gave a good real-world test of the fallback's graceful
  degradation — confirm your own deployment's egress allows this host).

## One operational note

Your uploaded project's `.env` contained a live Groq API key. It has been
removed from this deliverable and replaced with `.env.example` — add your
real key back in `.env` before deploying (and consider rotating that key
since it was in a zip that passed through this session).
