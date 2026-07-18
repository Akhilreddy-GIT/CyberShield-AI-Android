"""
CyberShield Agent — LangChain orchestration layer.

Uses LangChain's ChatGroq wrapper (same underlying Groq API/model as before,
now routed through LangChain's chat model interface, prompt templates, and
message history handling) per Phase 14 of the project spec: "LangChain
Agent, RAG, Conversation Memory, Prompt Templates."

The system prompt casts the assistant as an experienced cybercrime
investigator: it classifies the incident, asks one sharp follow-up question
at a time, and answers using a consistent structure (summary, risk, why,
immediate actions, reporting, evidence, legal guidance, prevention,
follow-up) rather than a generic paragraph. Structure is enforced through
the prompt, not hardcoded parsing — reply is still returned as a single
string, so ChatMessageOut's contract is unchanged and the Android app needs
no changes.

If GROQ_API_KEY is not set, falls back to template-based responses built
from the retrieved knowledge base — so the whole system is demoable and
testable by a reviewer BEFORE any key is added.
"""

import os
import logging
from typing import List, Optional

from langchain_groq import ChatGroq
from langchain_core.messages import HumanMessage, AIMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from app.knowledge_base.legal_kb import LegalDoc, HELPLINES, get_relevant_helplines
from app.knowledge_base.playbooks import match_playbook, build_playbook_block
from app.services.vector_rag import vector_search_kb as search_kb
from app.services.intent_classifier import IntentResult, resolve_kb_category
from app.services.risk_engine import RiskLevel
from app.services.case_memory_service import facts_summary_for_prompt

logger = logging.getLogger(__name__)

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
MODEL = "llama-3.1-8b-instant"

_llm: Optional[ChatGroq] = None
if GROQ_API_KEY:
    _llm = ChatGroq(api_key=GROQ_API_KEY, model=MODEL, temperature=0.3, max_tokens=900)
else:
    logger.warning("GROQ_API_KEY not set — agent_service will use template fallback responses only.")


SYSTEM_PROMPT = """You are CyberShield AI, a calm and experienced cybercrime investigator \
and cyber safety expert. You talk to victims the way a skilled human investigator would: \
you quickly understand what happened, you don't waste their time, and you tell them \
exactly what to do — not vague reassurance.

SCOPE — YOU ONLY DISCUSS:
phishing, UPI/QR/OTP/banking fraud, investment/crypto/job/loan-app scams, SIM swap, \
identity theft, cyberbullying, online harassment, stalking, revenge porn/sextortion, \
blackmail, ransomware, malware, hacked social media/email accounts, fake job/loan apps, \
child online safety, digital evidence, cyber law, reporting procedures, password \
security, data breaches, and account recovery.
If the user asks about anything else (movies, sports, coding help unrelated to an \
incident, politics, general trivia, homework, etc.) politely decline and redirect them \
back to cyber safety in one short sentence. Do not lecture them about it.

ADAPTIVE INTAKE — ASK BEFORE YOU ANSWER AT LENGTH:
If the message is vague or you're missing a detail that changes what to actually tell \
the user to do (e.g. "someone hacked my account" without which platform, or "I got \
scammed" without whether money actually moved), ask ONE sharp, specific follow-up \
question instead of guessing or giving a generic answer. Ask only the single most useful \
question — never a list of questions, never more than one per turn. Once you have enough \
to act (platform/type known, and whether money/credentials/access were actually lost), \
stop asking and give the full structured response below. Never ask about something \
already listed in KNOWN CASE FACTS below.

RESPONSE FORMAT — once you have enough information, structure your answer with these \
sections (use short bold headers, skip a section only if it's truly not applicable — \
e.g. no Evidence section for a purely informational question with no incident):

**Situation Summary** — one or two lines showing you understood what happened.

**Risk Level** — you MUST state exactly the level given to you in AUTHORITATIVE RISK \
ASSESSMENT below, verbatim, in all caps (LOW / MEDIUM / HIGH / CRITICAL). This has already \
been computed deterministically by the backend's risk engine from the full case — it is \
not yours to recalculate, soften, escalate, or second-guess. Add one line explaining why, \
tied to the specific factors listed in AUTHORITATIVE RISK ASSESSMENT and anything else the \
user told you. If AUTHORITATIVE RISK ASSESSMENT is not provided below (e.g. this is a pure \
informational question with no incident yet), you may omit this section entirely rather \
than inventing a level.

**Immediate Actions** — ordered checklist, highest priority first. Prefer the matched \
playbook's immediate actions below if one is provided; adapt wording to the case but \
don't contradict it.

**Next 24 Hours** — recovery/follow-up steps.

**Evidence To Preserve** — concrete list (screenshots, transaction IDs, phone numbers, \
chat logs, emails, etc.) specific to this incident type, not a generic list.

**Authorities** — only include ones actually relevant to this case (1930, \
cybercrime.gov.in, local police, bank/platform support, CERT-In, Women Helpline, \
Childline) — never list all of them by default.

**Prevention** — one or two specific recommendations tied to this incident, not generic advice.

**Follow-up Question** — ONE intelligent question that helps continue the investigation \
(skip this section if you already have everything needed to close out the guidance).

Keep it scannable: short bullet lines, not paragraphs of prose. If the user just asked a \
quick clarifying question rather than reporting a new incident, answer that directly \
instead of forcing the full template.

RULES:
1. Classify what kind of cybercrime this is, in your own words, early in the reply.
2. If this looks like an emergency (suicidal statements, credible physical threat, active \
blackmail/sextortion in progress, a minor at risk, or an account/money actively being \
drained right now), say so plainly and lead with the single most urgent action before \
anything else — even before you've finished gathering every detail.
3. Cite legal provisions and helpline numbers ONLY from the reference material given to \
you below. NEVER invent a section number, a punishment, or a helpline number. If the \
provided context doesn't cover the situation, say plainly: "I don't have enough verified \
information to answer that confidently — here's what I can tell you instead," and stick \
to what you do have.
4. Stay calm, non-judgmental, and trauma-informed. Never blame the user for clicking a \
link, sharing an OTP, or trusting someone. Victims of cybercrime are not at fault.
5. Never ask for the user's real name, address, or other identifying details unless they \
volunteer it — this is an anonymous-first system.
6. Use KNOWN CASE FACTS below (already extracted from this conversation) instead of \
re-asking for information the user already gave you.
7. If AUTHORITATIVE RISK ASSESSMENT is provided below, that is the ONLY risk level you may \
state anywhere in your reply — in the Risk Level section or elsewhere. Never state a \
different risk level than the one given, even if your own reading of the message suggests \
otherwise; report that disagreement is not your role here — the backend's deterministic \
risk engine is the single source of truth shown identically to the user in every other \
part of the app (case list, case details, timeline), and a mismatch is a bug, not a \
judgment call.

{context_block}

{playbook_block}

{facts_block}

{risk_block}

{emergency_block}"""

# LangChain prompt template (Phase 14: Prompt Templates) — system prompt +
# retrieved KB context + emergency framing, followed by the running
# conversation history (Phase 10: case-only memory, passed in per-request
# from the DB) and the newest user message.
_prompt_template = ChatPromptTemplate.from_messages([
    ("system", SYSTEM_PROMPT),
    MessagesPlaceholder(variable_name="history"),
    ("human", "{input}"),
])


def build_context_block(legal_docs: List[LegalDoc]) -> str:
    """Builds the RAG context block, deduplicating near-identical entries
    and keeping it concise so the model doesn't drown in repeated text."""
    if not legal_docs:
        return "No specific legal reference matched this query yet — do not invent one."
    lines = ["RETRIEVED LEGAL REFERENCES (only use these, do not invent others):"]
    seen_titles = set()
    for doc in legal_docs:
        if doc.title in seen_titles:
            continue
        seen_titles.add(doc.title)
        lines.append(f"- {doc.title}: {doc.summary} [Source: {doc.source_note}]")
    helpline_lines = ["", "VERIFIED HELPLINES (only use these numbers, never invent others):"]
    for h in HELPLINES:
        helpline_lines.append(f"- {h['name']}: {h['number']} — {h['use_for']}")
    return "\n".join(lines + helpline_lines)


def build_risk_block(risk_assessment: Optional[dict]) -> str:
    """Renders the backend's already-computed, persisted risk assessment as
    an authoritative prompt block. This is what fixes chat showing a
    different risk level than Cases/Case Details/Timeline: the model is no
    longer asked to freely judge risk in prose — it is given the exact
    value that has already been (or is about to be) written to the case
    row, and instructed to report that value verbatim. risk_assessment is
    None only when there's genuinely no incident yet (e.g. a pure
    educational question), in which case no risk framing is injected at all."""
    if not risk_assessment:
        return ""
    level = risk_assessment.get("level")
    score = risk_assessment.get("score")
    factors = risk_assessment.get("triggered_factors") or []
    factor_lines = "\n".join(f"- {f}" for f in factors) if factors else "- (no specific signals beyond the base classification)"
    return (
        "AUTHORITATIVE RISK ASSESSMENT (already computed by the backend risk engine — "
        "state this exact level, do not recompute):\n"
        f"- Risk Level: {level}\n"
        f"- Risk Score: {score}\n"
        f"- Contributing factors:\n{factor_lines}"
    )


def build_emergency_block(intent: IntentResult) -> str:
    """Adds explicit emergency framing to the prompt when the intent
    classifier detected one, so the model leads with urgent guidance
    instead of continuing a routine intake flow."""
    if not intent.is_critical:
        return ""
    emergency_guidance = {
        "self_harm": (
            "EMERGENCY DETECTED: the user's message contains language suggesting they may be "
            "considering self-harm or suicide. Before anything else, gently and directly "
            "acknowledge this, encourage them to reach out to a crisis helpline right now, and "
            "mention the iCall Psychosocial Helpline from the reference list. Do not treat this "
            "turn as a routine cybercrime intake question — their safety comes first."
        ),
        "imminent_danger": (
            "EMERGENCY DETECTED: possible credible physical danger. Lead with: if they are in "
            "immediate physical danger, contact Police Emergency (112) right now. Then continue "
            "with cyber-specific guidance."
        ),
        "child_safety": (
            "EMERGENCY DETECTED: this may involve a minor. Treat this as the highest priority. "
            "Recommend Childline India (1098) and immediate reporting via cybercrime.gov.in, and "
            "keep your language extra calm and careful — do not ask for graphic detail."
        ),
        "active_exploitation": (
            "EMERGENCY DETECTED: active blackmail or non-consensual explicit content sharing in "
            "progress. Lead with: do not pay, preserve every message/screenshot as evidence, and "
            "report immediately via cybercrime.gov.in or 1930."
        ),
        "account_takeover": (
            "EMERGENCY DETECTED: an account or funds may be actively compromised right now. Lead "
            "with the single fastest containment step (e.g. call bank/1930 immediately for money, "
            "or platform account-recovery for a hacked account) before anything else."
        ),
    }
    return emergency_guidance.get(
        intent.emergency_type or "",
        "EMERGENCY DETECTED: prioritize immediate safety guidance over routine information gathering.",
    )


def _to_langchain_messages(history: List[dict]) -> List:
    """Convert stored {role, content} dicts into LangChain message objects."""
    converted = []
    for m in history[-10:]:  # Phase 10: keep recent case-only context only
        if m["role"] == "user":
            converted.append(HumanMessage(content=m["content"]))
        else:
            converted.append(AIMessage(content=m["content"]))
    return converted


def generate_response(
    user_message: str,
    conversation_history: List[dict],
    intent: IntentResult,
    facts_json: Optional[str] = None,
    risk_assessment: Optional[dict] = None,
) -> dict:
    """
    Returns a dict: { reply: str, cited_sources: [...], used_llm: bool }
    Contract unchanged from the original implementation — callers (chat
    router, Android app) need no changes. facts_json is an optional new
    parameter (persisted per-case memory); existing callers that don't
    pass it still work exactly as before since it defaults to None.

    risk_assessment (optional): {"level": str, "score": int,
    "triggered_factors": [str]} — the backend's own already-computed risk
    for this case (see risk_engine.py). When provided, this is the ONLY
    risk level the generated reply is allowed to state (see
    build_risk_block); this is what keeps Chat, Cases, Case Details and
    Timeline showing identical risk values instead of the model
    independently re-judging severity in prose. Callers that don't pass it
    get a reply with no Risk Level section (e.g. pure educational
    questions with no case/incident) rather than a fabricated one.
    """
    # Search using recent context, not just the latest message in isolation
    # — a short follow-up like "2 hours ago" or "no I don't have access"
    # carries almost no topical signal on its own and would otherwise
    # hijack retrieval into an unrelated legal doc, especially damaging in
    # the no-LLM template fallback path which has no other way to stay
    # on-topic across turns.
    recent_user_turns = [m["content"] for m in conversation_history[-4:] if m.get("role") == "user"]
    search_query = " ".join(recent_user_turns + [user_message])
    legal_docs = search_kb(search_query, category=resolve_kb_category(intent.category.value if intent.category else None))
    context_block = build_context_block(legal_docs)
    emergency_block = build_emergency_block(intent)

    playbook = match_playbook(search_query, intent.category.value if intent.category else None)
    playbook_block = build_playbook_block(playbook)
    facts_block = facts_summary_for_prompt(facts_json)
    risk_block = build_risk_block(risk_assessment)

    if _llm is not None:
        try:
            chain = _prompt_template | _llm
            result = chain.invoke({
                "context_block": context_block,
                "playbook_block": playbook_block,
                "facts_block": facts_block,
                "risk_block": risk_block,
                "emergency_block": emergency_block,
                "history": _to_langchain_messages(conversation_history),
                "input": user_message,
            })
            reply = result.content
            return {
                "reply": reply,
                "cited_sources": [d.title for d in legal_docs],
                "used_llm": True,
            }
        except Exception as e:
            logger.error("LLM call failed, using template fallback: %s", e)
            fallback_note = f" (LLM call failed: {str(e)[:100]}, using fallback)"
            return _template_response(legal_docs, intent, fallback_note, playbook, risk_assessment)

    return _template_response(legal_docs, intent, "", playbook, risk_assessment)


def _template_response(legal_docs: List[LegalDoc], intent: IntentResult, note: str, playbook=None, risk_assessment: Optional[dict] = None) -> dict:
    """No-API-key fallback. Deterministic, still structured (summary / risk
    framing / next steps), and fully honest about being a template rather
    than a generated response. Now also pulls from a matched playbook when
    available for more specific, incident-tailored actions."""
    if intent.is_critical:
        header = "**This looks time-sensitive — here is the most important step first.**\n\n"
    else:
        header = ""

    if not legal_docs and not playbook:
        body = (
            header +
            "I don't have enough verified information yet to point you to a specific legal "
            "provision. Could you tell me a bit more — which platform this happened on, "
            "roughly when, and whether any money, passwords, OTPs, or personal documents "
            "(like Aadhaar/PAN) were involved?"
        )
    else:
        relevant_helplines = get_relevant_helplines(
            category=intent.category.value if intent.category else None,
            emergency_type=intent.emergency_type,
        )
        helpline_lines = "\n".join(f"- {h['name']}: {h['number']}" for h in relevant_helplines)

        if playbook:
            immediate = "\n".join(f"- {a}" for a in playbook.immediate_actions[:5])
            next24 = "\n".join(f"- {a}" for a in playbook.next_24_hours[:4])
            evidence = "\n".join(f"- {a}" for a in playbook.evidence_to_preserve[:5])
            title_line = f"**Likely category:** {playbook.name}\n\n"
        else:
            immediate = (
                "- Preserve all evidence (screenshots, messages, transaction IDs) — don't delete anything.\n"
                "- File a complaint at cybercrime.gov.in.\n"
                "- If money or credentials were involved, call 1930 (National Cyber Crime Helpline) immediately."
            )
            next24 = ""
            evidence = ""
            title_line = ""

        doc_section = ""
        if legal_docs:
            doc = legal_docs[0]
            applicable = "; ".join(doc.applicable_when[:2])
            doc_section = f"{doc.summary}\n\n**This may apply if:** {applicable}\n\n"
            if not playbook:
                title_line = f"**Likely category:** {doc.title}\n\n"

        # Same authoritative value the LLM path is instructed to echo — the
        # template fallback must never omit or freely restate risk either,
        # or a demo running without GROQ_API_KEY would reintroduce the exact
        # chat-vs-case mismatch this system is required to prevent.
        risk_line = ""
        if risk_assessment and risk_assessment.get("level"):
            risk_line = f"**Risk Level:** {risk_assessment['level']} (score {risk_assessment.get('score', 0)})\n\n"

        body = (
            header +
            title_line +
            risk_line +
            doc_section +
            f"**Immediate actions:**\n{immediate}\n\n"
            + (f"**Next 24 hours:**\n{next24}\n\n" if next24 else "")
            + (f"**Evidence to preserve:**\n{evidence}\n\n" if evidence else "")
            + f"**Helplines:**\n{helpline_lines}\n\n"
            f"(Note: LLM API key not configured — this is a knowledge-base template "
            f"response, not a generated one.{note})"
        )
    return {
        "reply": body,
        "cited_sources": [d.title for d in legal_docs],
        "used_llm": False,
    }


def get_helplines_for_critical(category: Optional[str] = None, emergency_type: Optional[str] = None) -> list:
    """Returns ONLY the helplines relevant to the given incident category
    (and emergency type, if any) — not the full static helpline list.
    category/emergency_type are optional so any existing caller that hasn't
    been updated yet still gets a sane generic default (national cybercrime
    helpline + portal) instead of breaking."""
    return get_relevant_helplines(category=category, emergency_type=emergency_type)
