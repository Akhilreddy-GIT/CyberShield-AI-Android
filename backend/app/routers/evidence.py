import os
import uuid
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, Form
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, Evidence
from app.services.ocr_service import extract_text_from_image

router = APIRouter(prefix="/api/evidence", tags=["evidence"])

UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "..", "..", "uploads")
os.makedirs(UPLOAD_DIR, exist_ok=True)

ALLOWED_EXTENSIONS = {".png", ".jpg", ".jpeg", ".pdf", ".txt", ".mp3", ".mp4", ".wav", ".m4a", ".docx"}
MAX_FILE_SIZE = 20 * 1024 * 1024  # 20MB


@router.post("/upload")
async def upload_evidence(
    case_id: str = Form(...),
    description: str = Form(""),
    file: UploadFile = File(...),
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

    return {
        "id": evidence.id,
        "filename": evidence.filename,
        "file_type": evidence.file_type,
        "uploaded_at": evidence.uploaded_at,
        "extracted_text": evidence.extracted_text,
    }


@router.get("/{case_id}")
def list_evidence(case_id: str, db: Session = Depends(get_db)):
    items = db.query(Evidence).filter(Evidence.case_id == case_id).order_by(Evidence.uploaded_at).all()
    return [
        {"id": e.id, "filename": e.filename, "file_type": e.file_type, "description": e.description,
         "extracted_text": e.extracted_text, "uploaded_at": e.uploaded_at}
        for e in items
    ]


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
