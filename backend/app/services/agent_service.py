"""
CyberShield Agent — LangChain orchestration layer.

Uses LangChain's ChatGroq wrapper (same underlying Groq API/model as before,
now routed through LangChain's chat model interface, prompt templates, and
message history handling) per Phase 14 of the project spec: "LangChain
Agent, RAG, Conversation Memory, Prompt Templates."

If GROQ_API_KEY is not set, falls back to template-based responses built
from the retrieved knowledge base — so the whole system is demoable and
testable by a reviewer BEFORE any key is added.
"""

import os
from typing import List, Optional

from langchain_groq import ChatGroq
from langchain_core.messages import HumanMessage, AIMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder

from app.knowledge_base.legal_kb import LegalDoc, HELPLINES
from app.services.vector_rag import vector_search_kb as search_kb
from app.services.intent_classifier import IntentResult

GROQ_API_KEY = os.getenv("GROQ_API_KEY")
MODEL = "llama-3.1-8b-instant"

_llm: Optional[ChatGroq] = None
if GROQ_API_KEY:
    _llm = ChatGroq(api_key=GROQ_API_KEY, model=MODEL, temperature=0.4, max_tokens=500)


SYSTEM_PROMPT = """You are CyberShield AI, an expert cyber assistance system. Your sole \
responsibility is assisting users with cybercrime, cyberbullying, online harassment, \
sextortion, identity theft, scams, hacking, impersonation, digital abuse, and related \
legal guidance.

RULES YOU MUST FOLLOW:
1. You ONLY discuss cyber safety and cybercrime topics. Refuse everything else.
2. You NEVER invent legal information. Only use the legal reference material provided \
to you in the context below. If it doesn't cover the situation, say: "I don't have \
enough verified information to answer confidently."
3. You are conducting a structured interview to understand an incident — ask ONE \
clear follow-up question at a time, don't overwhelm the user.
4. You are calm, non-judgmental, and trauma-informed. Never blame the user.
5. You never ask for the user's real name, address, or identity unless they offer it \
voluntarily — this is an anonymous-first system.
6. If the situation involves a minor, credible threat of violence, or immediate danger, \
prioritize safety guidance over information-gathering.

Always be concise. This is a chat interface, not an essay.

{context_block}"""

# LangChain prompt template (Phase 14: Prompt Templates) — system prompt +
# retrieved KB context, followed by the running conversation history
# (Phase 10: case-only memory, passed in per-request from the DB) and the
# newest user message.
_prompt_template = ChatPromptTemplate.from_messages([
    ("system", SYSTEM_PROMPT),
    MessagesPlaceholder(variable_name="history"),
    ("human", "{input}"),
])


def build_context_block(legal_docs: List[LegalDoc]) -> str:
    if not legal_docs:
        return "No specific legal reference matched this query yet."
    lines = ["RETRIEVED LEGAL REFERENCES (only use these, do not invent others):"]
    for doc in legal_docs:
        lines.append(f"- {doc.title}: {doc.summary} [Source: {doc.source_note}]")
    return "\n".join(lines)


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
) -> dict:
    """
    Returns a dict: { reply: str, cited_sources: [...], used_llm: bool }
    """
    legal_docs = search_kb(user_message, category=intent.category.value if intent.category else None)
    context_block = build_context_block(legal_docs)

    if _llm is not None:
        try:
            chain = _prompt_template | _llm
            result = chain.invoke({
                "context_block": context_block,
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
            fallback_note = f" (LLM call failed: {str(e)[:100]}, using fallback)"
            return _template_response(legal_docs, intent, fallback_note)

    return _template_response(legal_docs, intent, "")


def _template_response(legal_docs: List[LegalDoc], intent: IntentResult, note: str) -> dict:
    """No-API-key fallback. Deterministic, still useful, fully honest about being a template."""
    if not legal_docs:
        body = (
            "I don't have enough verified information to answer confidently yet. "
            "Could you tell me a bit more about what happened — what platform it "
            "occurred on, and roughly when?"
        )
    else:
        doc = legal_docs[0]
        body = (
            f"Based on what you've described, this may relate to: **{doc.title}**.\n\n"
            f"{doc.summary}\n\n"
            f"This could apply if: {'; '.join(doc.applicable_when[:2])}.\n\n"
            f"(Note: LLM API key not configured — this is a knowledge-base template "
            f"response, not a generated one.{note})"
        )
    return {
        "reply": body,
        "cited_sources": [d.title for d in legal_docs],
        "used_llm": False,
    }


def get_helplines_for_critical() -> list:
    return HELPLINES
