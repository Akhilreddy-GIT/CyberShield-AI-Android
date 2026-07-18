import os
import uuid
import mimetypes
import logging
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, Evidence, TimelineEvent
from app.services.ocr_service import extract_text_from_image
from app.services.evidence_intelligence_service import analyze_evidence_text
from app.services.evidence_analyzer_service import generate_evidence_analysis
from app.services.case_memory_service import update_case_facts
from app.services.timeline_service import extract_timeline_events, dedupe_against_existing, evidence_source_note
from app.services.recovery_lifecycle_service import compute_recovery_state_for_case
from app.routers.ws_case import broadcast_case_update

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/evidence", tags=["evidence"])

UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)

ALLOWED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".pdf", ".txt", ".mp3", ".mp4", ".wav", ".m4a", ".docx"}
MAX_FILE_SIZE = 20 * 1024 * 1024  # 20MB


def _file_url(case_id: str, evidence_id: str) -> str:
    # Relative path — the Android client already prepends its configured
    # API base URL to every other endpoint, so we stay consistent with
    # that instead of hardcoding a host here.
    return f"/api/evidence/file/{case_id}/{evidence_id}"


def _media_type_for(filename: str, file_type: str | None) -> str:
    guessed, _ = mimetypes.guess_type(filename)
    if guessed:
        return guessed
    # Fallback for extensions mimetypes may not know locally.
    fallback = {
        ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
        ".pdf": "application/pdf", ".txt": "text/plain",
        ".mp3": "audio/mpeg", ".mp4": "video/mp4", ".wav": "audio/wav",
        ".m4a": "audio/mp4",
        ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    }
    return fallback.get((file_type or "").lower(), "application/octet-stream")


@router.post("/upload")
async def upload_evidence(
    case_id: str = Form(...),
    description: str = Form(""),
    file: UploadFile = File(...),
    qr_content: str = Form(""),
    db: Session = Depends(get_db),
):
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")

    ext = os.path.splitext(file.filename)[1].lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(400, f"File type {ext} not allowed. Allowed: {ALLOWED_EXTENSIONS}")

    contents = await file.read()
    if len(contents) > MAX_FILE_SIZE:
        raise HTTPException(400, "File too large (max 20MB)")

    safe_name = f"{uuid.uuid4().hex[:12]}{ext}"
    case_dir = os.path.join(UPLOAD_DIR, case_id)
    os.makedirs(case_dir, exist_ok=True)
    stored_path = os.path.join(case_dir, safe_name)

    with open(stored_path, "wb") as f:
        f.write(contents)

    # Real OCR extraction — runs synchronously since Tesseract is fast
    # enough for single-image uploads; never blocks or fails the upload.
    extracted_text = extract_text_from_image(stored_path)
    combined_text = " ".join(t for t in [extracted_text, description] if t)

    # Evidence Intelligence Engine: structured entity extraction (phone
    # numbers, emails, URLs, domains, UPI IDs, banks, transaction IDs, QR
    # content, dates, times, social handles) + a human-readable summary of
    # what was found and what's suspicious about it. Never blocks the
    # upload — falls back to an empty/neutral result on any failure.
    intelligence = {"entities": {}, "suspicious_markers": [], "summary": "No readable text was found in this file — nothing could be automatically extracted."}
    try:
        intelligence = analyze_evidence_text(extracted_text or "", qr_content=qr_content or None)
    except Exception:
        logger.exception("Evidence intelligence extraction failed for %s; continuing without it.", file.filename)

    # AI Evidence Analyzer: scam-pattern / social-engineering analysis of
    # the extracted text (urgency manipulation, fake authority, requests
    # for money/OTP/credentials, etc.). Rule-based layer always succeeds;
    # LLM narrative layer is best-effort on top of it.
    scam_analysis = {"message_type": "unknown", "threat_level": "informational", "indicators": [], "requests_money": False, "requests_otp_or_credentials": False, "narrative": "No text available to analyze.", "used_llm": False}
    try:
        scam_analysis = generate_evidence_analysis(extracted_text or "")
    except Exception:
        logger.exception("Evidence scam analysis failed for %s; continuing without it.", file.filename)

    evidence = Evidence(
        case_id=case_id,
        filename=file.filename,
        stored_path=stored_path,
        file_type=ext,
        description=description,
        extracted_text=extracted_text,
    )
    db.add(evidence)
    db.commit()
    db.refresh(evidence)

    # Feed evidence-derived entities into the same per-case fact memory used
    # by chat (Case.facts_json) so the AI agent also knows about phone
    # numbers/UPI IDs/etc. found in a screenshot, not just ones typed in
    # chat, and never re-asks for something visible in the evidence itself.
    try:
        if combined_text:
            case.facts_json = update_case_facts(case.facts_json, combined_text)
            db.commit()
    except Exception:
        logger.exception("Failed to merge evidence entities into case facts for case %s.", case_id)

    # Incident Timeline Engine: detect incident-lifecycle actions mentioned
    # in the evidence text (e.g. a screenshot of an OTP request, or a
    # description saying "money was transferred") and log them, tagged with
    # their evidence source, skipping stages already recorded for this case.
    new_timeline_events = []
    try:
        if combined_text:
            candidates = extract_timeline_events(combined_text)
            if candidates:
                existing_descriptions = [
                    e.description for e in
                    db.query(TimelineEvent).filter(TimelineEvent.case_id == case_id).all()
                ]
                source_note = evidence_source_note(file.filename)
                for c in dedupe_against_existing(candidates, existing_descriptions):
                    event = TimelineEvent(
                        case_id=case_id,
                        description=f"{c.description} {source_note}",
                        event_time=c.event_time,
                    )
                    db.add(event)
                    new_timeline_events.append(event)
                if new_timeline_events:
                    db.commit()
                    for e in new_timeline_events:
                        db.refresh(e)
    except Exception:
        logger.exception("Timeline auto-extraction from evidence failed for case %s.", case_id)

    if new_timeline_events:
        await broadcast_case_update(case_id, {
            "case_id": case_id,
            "timeline_events_added": [
                {"id": e.id, "description": e.description, "event_time": e.event_time, "created_at": e.created_at.isoformat()}
                for e in new_timeline_events
            ],
        })

    # Uploading evidence is itself a real recovery-lifecycle-advancing
    # action (see recovery_lifecycle_service.py) — recompute and broadcast
    # so any connected client's case-details view updates without a
    # separate poll, same as a risk-level change does.
    recovery = compute_recovery_state_for_case(case, db)
    await broadcast_case_update(case_id, {
        "case_id": case_id,
        "recovery_stage": recovery.stage,
        "recovery_progress_percent": recovery.progress_percent,
    })

    return {
        "id": evidence.id,
        "filename": evidence.filename,
        "file_type": evidence.file_type,
        "uploaded_at": evidence.uploaded_at,
        "extracted_text": evidence.extracted_text,
        # New, additive fields — existing Android clients that only read
        # the fields above continue to work unchanged.
        "intelligence": intelligence,
        "scam_analysis": scam_analysis,
        "recovery_stage": recovery.stage,
        "recovery_progress_percent": recovery.progress_percent,
        # New, additive field for viewing the uploaded file.
        "file_url": _file_url(case_id, evidence.id),
        "download_url": _file_url(case_id, evidence.id),
    }


@router.get("/{case_id}")
def list_evidence(case_id: str, db: Session = Depends(get_db)):
    items = db.query(Evidence).filter(Evidence.case_id == case_id).order_by(Evidence.uploaded_at).all()
    return [
        {"id": e.id, "filename": e.filename, "file_type": e.file_type, "description": e.description,
         "extracted_text": e.extracted_text, "uploaded_at": e.uploaded_at,
         # New, additive fields — existing Android clients that only read
         # the fields above continue to work unchanged.
         "file_url": _file_url(case_id, e.id),
         "download_url": _file_url(case_id, e.id)}
        for e in items
    ]


@router.get("/file/{case_id}/{evidence_id}")
def get_evidence_file(case_id: str, evidence_id: str, db: Session = Depends(get_db)):
    # Scoped lookup: the evidence row must belong to the case_id in the
    # URL, so a client can never fetch another case's evidence just by
    # guessing/incrementing an evidence_id.
    evidence = (
        db.query(Evidence)
        .filter(Evidence.id == evidence_id, Evidence.case_id == case_id)
        .first()
    )
    if not evidence:
        raise HTTPException(404, "Evidence not found for this case")

    if not os.path.exists(evidence.stored_path):
        raise HTTPException(404, "Stored file is missing on the server")

    return FileResponse(
        path=evidence.stored_path,
        media_type=_media_type_for(evidence.filename, evidence.file_type),
        filename=evidence.filename,
    )


@router.delete("/{evidence_id}")
def delete_evidence(evidence_id: str, db: Session = Depends(get_db)):
    evidence = db.query(Evidence).filter(Evidence.id == evidence_id).first()
    if not evidence:
        raise HTTPException(404, "Evidence not found")
    if os.path.exists(evidence.stored_path):
        os.remove(evidence.stored_path)
    db.delete(evidence)
    db.commit()
    return {"status": "deleted"}
