from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.models.db import get_db, Case, Message
from app.models.schemas import ChatMessageIn, ChatMessageOut
from app.services.intent_classifier import classify_intent, REFUSAL_MESSAGE, CyberCategory
from app.services.agent_service import generate_response, get_helplines_for_critical
from app.routers.ws_case import broadcast_case_update

router = APIRouter(prefix="/api/chat", tags=["chat"])


@router.post("", response_model=ChatMessageOut)
async def send_message(payload: ChatMessageIn, db: Session = Depends(get_db)):
    intent = classify_intent(payload.message)

    # Get or create case
    if payload.case_id:
        case = db.query(Case).filter(Case.id == payload.case_id).first()
    else:
        case = None

    if not case:
        case = Case(anon_user_id=payload.anon_user_id, category=intent.category.value if intent.category else None)
        db.add(case)
        db.commit()
        db.refresh(case)

    # Domain guardrail — Phase 3
    if not intent.is_cyber_related:
        db.add(Message(case_id=case.id, role="user", content=payload.message))
        db.add(Message(case_id=case.id, role="assistant", content=REFUSAL_MESSAGE))
        db.commit()
        return ChatMessageOut(
            case_id=case.id,
            reply=REFUSAL_MESSAGE,
            category=None,
            is_critical=False,
            cited_sources=[],
            used_llm=False,
        )

    # Build conversation history for this case only (Phase 10: case-only memory)
    history = [
        {"role": m.role, "content": m.content}
        for m in db.query(Message).filter(Message.case_id == case.id).order_by(Message.created_at).all()
    ]

    result = generate_response(payload.message, history, intent)

    # Persist
    db.add(Message(case_id=case.id, role="user", content=payload.message))
    db.add(Message(
        case_id=case.id,
        role="assistant",
        content=result["reply"],
        cited_sources=",".join(result["cited_sources"]),
    ))
    if intent.category and intent.category != CyberCategory.OTHER_CYBER:
        case.category = intent.category.value
    db.commit()

    helplines = get_helplines_for_critical() if intent.is_critical else None
    if intent.is_critical:
        case.status = "escalated"
        case.risk_level = "Critical Emergency"
        case.risk_score = max(case.risk_score or 0, 10)
        db.commit()
        await broadcast_case_update(case.id, {
            "case_id": case.id,
            "status": case.status,
            "risk_level": case.risk_level,
            "risk_score": case.risk_score,
        })

    return ChatMessageOut(
        case_id=case.id,
        reply=result["reply"],
        category=case.category,
        is_critical=intent.is_critical,
        cited_sources=result["cited_sources"],
        used_llm=result["used_llm"],
        helplines=helplines,
    )


@router.get("/{case_id}/history")
def get_history(case_id: str, db: Session = Depends(get_db)):
    messages = db.query(Message).filter(Message.case_id == case_id).order_by(Message.created_at).all()
    return [
        {"role": m.role, "content": m.content, "cited_sources": (m.cited_sources or "").split(",") if m.cited_sources else [], "created_at": m.created_at}
        for m in messages
    ]
