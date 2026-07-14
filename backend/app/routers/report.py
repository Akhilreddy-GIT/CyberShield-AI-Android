import os
import io
from datetime import datetime
from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.lib import colors
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle

from app.models.db import get_db, Case, Message, Evidence, TimelineEvent
from app.services.vector_rag import vector_search_kb as search_kb

router = APIRouter(prefix="/api/report", tags=["report"])


def _build_report_data(case_id: str, db: Session) -> dict:
    case = db.query(Case).filter(Case.id == case_id).first()
    if not case:
        raise HTTPException(404, "Case not found")

    messages = db.query(Message).filter(Message.case_id == case_id).order_by(Message.created_at).all()
    evidence = db.query(Evidence).filter(Evidence.case_id == case_id).order_by(Evidence.uploaded_at).all()
    timeline = db.query(TimelineEvent).filter(TimelineEvent.case_id == case_id).order_by(TimelineEvent.created_at).all()

    user_messages = [m.content for m in messages if m.role == "user"]
    incident_summary = " ".join(user_messages[:3])[:500] if user_messages else "No details recorded yet."

    legal_refs = search_kb(incident_summary, category=case.category, top_k=3) if case.category else []

    recommended_actions = [
        "Preserve all evidence (screenshots, chat logs) — do not delete anything, even if distressing.",
        "File a complaint at the National Cyber Crime Reporting Portal: cybercrime.gov.in",
        "If financial fraud is involved, call 1930 (National Cyber Crime Helpline) immediately.",
    ]
    if case.risk_level in ("High Risk", "Critical Emergency"):
        recommended_actions.insert(0, "This case has been flagged for priority attention. Consider contacting local police (112) or the relevant helpline directly.")

    return {
        "case": case,
        "messages": messages,
        "evidence": evidence,
        "timeline": timeline,
        "incident_summary": incident_summary,
        "legal_refs": legal_refs,
        "recommended_actions": recommended_actions,
    }


@router.get("/{case_id}")
def get_report_json(case_id: str, db: Session = Depends(get_db)):
    data = _build_report_data(case_id, db)
    case = data["case"]
    return {
        "case_id": case.id,
        "category": case.category,
        "risk_level": case.risk_level,
        "risk_score": case.risk_score,
        "status": case.status,
        "created_at": case.created_at,
        "incident_summary": data["incident_summary"],
        "timeline": [{"description": t.description, "event_time": t.event_time} for t in data["timeline"]],
        "evidence": [{"filename": e.filename, "file_type": e.file_type, "description": e.description} for e in data["evidence"]],
        "legal_references": [{"title": d.title, "summary": d.summary, "source": d.source_note} for d in data["legal_refs"]],
        "recommended_actions": data["recommended_actions"],
    }


@router.get("/{case_id}/pdf")
def get_report_pdf(case_id: str, db: Session = Depends(get_db)):
    data = _build_report_data(case_id, db)
    case = data["case"]

    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4, topMargin=2*cm, bottomMargin=2*cm)
    styles = getSampleStyleSheet()

    title_style = ParagraphStyle("TitleCS", parent=styles["Title"], textColor=colors.HexColor("#1a1a2e"))
    heading_style = ParagraphStyle("HeadingCS", parent=styles["Heading2"], textColor=colors.HexColor("#0f3460"), spaceBefore=14, spaceAfter=6)
    body_style = styles["BodyText"]
    small_style = ParagraphStyle("Small", parent=styles["BodyText"], fontSize=8, textColor=colors.grey)

    story = []
    story.append(Paragraph("CyberShield AI — Case Report", title_style))
    story.append(Paragraph(f"Generated: {datetime.now().strftime('%d %B %Y, %H:%M')}", small_style))
    story.append(Spacer(1, 0.5*cm))

    meta_table_data = [
        ["Case ID", case.id],
        ["Category", (case.category or "Uncategorized").replace("_", " ").title()],
        ["Risk Level", case.risk_level],
        ["Status", case.status.replace("_", " ").title()],
        ["Created", case.created_at.strftime("%d %B %Y")],
    ]
    meta_table = Table(meta_table_data, colWidths=[4*cm, 10*cm])
    meta_table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#e8eaf6")),
        ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
        ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cccccc")),
        ("PADDING", (0, 0), (-1, -1), 6),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 0.5*cm))

    story.append(Paragraph("Incident Summary", heading_style))
    story.append(Paragraph(data["incident_summary"] or "No details recorded.", body_style))

    story.append(Paragraph("Timeline", heading_style))
    if data["timeline"]:
        for t in data["timeline"]:
            story.append(Paragraph(f"• {t.event_time + ' — ' if t.event_time else ''}{t.description}", body_style))
    else:
        story.append(Paragraph("No timeline events recorded.", body_style))

    story.append(Paragraph("Collected Evidence", heading_style))
    if data["evidence"]:
        ev_data = [["Filename", "Type", "Description"]] + [
            [e.filename, e.file_type or "-", (e.description or "-")[:60]] for e in data["evidence"]
        ]
        ev_table = Table(ev_data, colWidths=[5*cm, 2.5*cm, 6.5*cm])
        ev_table.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0f3460")),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
            ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cccccc")),
            ("FONTSIZE", (0, 0), (-1, -1), 8),
            ("PADDING", (0, 0), (-1, -1), 5),
        ]))
        story.append(ev_table)
    else:
        story.append(Paragraph("No evidence uploaded yet.", body_style))

    story.append(Paragraph("Relevant Legal References", heading_style))
    if data["legal_refs"]:
        for d in data["legal_refs"]:
            story.append(Paragraph(f"<b>{d.title}</b>", body_style))
            story.append(Paragraph(d.summary, body_style))
            story.append(Paragraph(f"<i>Source: {d.source_note}</i>", small_style))
            story.append(Spacer(1, 0.2*cm))
    else:
        story.append(Paragraph("No specific legal references matched yet — insufficient information gathered.", body_style))

    story.append(Paragraph("Recommended Next Steps", heading_style))
    for action in data["recommended_actions"]:
        story.append(Paragraph(f"• {action}", body_style))

    story.append(Spacer(1, 0.8*cm))
    story.append(Paragraph(
        "DISCLAIMER: This report is generated by an academic project system and is NOT a "
        "substitute for professional legal advice or an official police report. Legal "
        "references are summarized from public sources and must be independently verified. "
        "This tool does not store passwords or unrelated personal information, and operates "
        "on an anonymous-first basis.",
        small_style,
    ))

    doc.build(story)
    buffer.seek(0)

    return StreamingResponse(
        buffer,
        media_type="application/pdf",
        headers={"Content-Disposition": f"attachment; filename={case.id}_report.pdf"},
    )
