"""
CyberShield AI — Legal Knowledge Base

IMPORTANT: This is a curated reference set for an academic/demo project.
It summarizes publicly known provisions of the Indian IT Act 2000 (as amended)
and the Bharatiya Nyaya Sanhita (BNS) 2023, which replaced the IPC.
This is NOT legal advice and has NOT been reviewed by a lawyer.
Before any real-world use, every entry here must be verified against the
official, current text of the law by a qualified legal professional.

Each entry has an explicit source_note so the RAG layer can honestly cite
"where this claim comes from" and the disclaimer can be shown to users.
"""

from dataclasses import dataclass, field
from typing import List


@dataclass
class LegalDoc:
    id: str
    category: str
    title: str
    summary: str
    applicable_when: List[str]
    source_note: str
    keywords: List[str] = field(default_factory=list)


LEGAL_KNOWLEDGE_BASE: List[LegalDoc] = [
    LegalDoc(
        id="it_act_66c",
        category="identity_theft",
        title="IT Act 2000 — Section 66C (Identity Theft)",
        summary=(
            "Covers fraudulent or dishonest use of another person's identifying "
            "information such as electronic signatures, passwords, or other "
            "unique identification features. Punishable with imprisonment up to "
            "3 years and a fine up to ₹1 lakh."
        ),
        applicable_when=[
            "Someone used your photos, name, or details to create a fake account",
            "Someone accessed your account using stolen credentials",
            "Someone is impersonating you online",
        ],
        source_note="Public summary of IT Act 2000, Section 66C (as amended 2008).",
        keywords=["identity theft", "impersonation", "fake profile", "stolen password", "fake account"],
    ),
    LegalDoc(
        id="it_act_66d",
        category="impersonation_fraud",
        title="IT Act 2000 — Section 66D (Cheating by Personation using Computer Resource)",
        summary=(
            "Covers cheating someone by pretending to be another person using a "
            "computer resource or communication device. Punishable with "
            "imprisonment up to 3 years and a fine up to ₹1 lakh."
        ),
        applicable_when=[
            "Someone pretended to be another person to defraud or trick you",
            "Fake profile used to extract money or personal info",
        ],
        source_note="Public summary of IT Act 2000, Section 66D.",
        keywords=["scam", "fraud", "cheating", "fake profile", "impersonation"],
    ),
    LegalDoc(
        id="it_act_66e",
        category="privacy_violation",
        title="IT Act 2000 — Section 66E (Violation of Privacy)",
        summary=(
            "Covers capturing, publishing, or transmitting images of a person's "
            "private parts or private acts without consent. Punishable with "
            "imprisonment up to 3 years or a fine up to ₹2 lakh, or both."
        ),
        applicable_when=[
            "Someone photographed or filmed you without consent in a private setting",
            "Private images were shared without your permission",
        ],
        source_note="Public summary of IT Act 2000, Section 66E.",
        keywords=["privacy", "image", "photo", "video", "consent", "captured"],
    ),
    LegalDoc(
        id="it_act_67",
        category="obscene_content",
        title="IT Act 2000 — Section 67 (Publishing Obscene Material)",
        summary=(
            "Covers publishing or transmitting obscene material in electronic "
            "form. First conviction: up to 3 years imprisonment and fine up to "
            "₹5 lakh. Subsequent conviction: up to 5 years and fine up to ₹10 lakh."
        ),
        applicable_when=[
            "Explicit content about you was shared online without consent",
            "Someone is threatening to publish intimate content",
        ],
        source_note="Public summary of IT Act 2000, Section 67.",
        keywords=["obscene", "explicit", "sextortion", "nude", "intimate images", "revenge porn"],
    ),
    LegalDoc(
        id="it_act_67a",
        category="sexually_explicit_content",
        title="IT Act 2000 — Section 67A (Sexually Explicit Act Material)",
        summary=(
            "Covers publishing or transmitting material containing sexually "
            "explicit acts in electronic form. First conviction: up to 5 years "
            "imprisonment and fine up to ₹10 lakh."
        ),
        applicable_when=[
            "Sexually explicit content involving you was shared or is being threatened to be shared",
        ],
        source_note="Public summary of IT Act 2000, Section 67A.",
        keywords=["sextortion", "explicit content", "blackmail", "nude photos"],
    ),
    LegalDoc(
        id="it_act_67b",
        category="child_exploitation",
        title="IT Act 2000 — Section 67B (Child Sexual Abuse Material)",
        summary=(
            "Covers publishing, browsing, downloading, or transmitting material "
            "depicting children in a sexually explicit manner, and online "
            "solicitation of children. Carries severe penalties, up to 5-7 years "
            "imprisonment and significant fines. This is treated as a CRITICAL "
            "emergency category requiring immediate escalation."
        ),
        applicable_when=[
            "A minor is involved as a victim of sexual content or solicitation",
            "Any suspected child exploitation material",
        ],
        source_note="Public summary of IT Act 2000, Section 67B. Also covered under POCSO Act 2012.",
        keywords=["child", "minor", "csam", "solicitation", "grooming"],
    ),
    LegalDoc(
        id="bns_stalking",
        category="stalking",
        title="Bharatiya Nyaya Sanhita (BNS) 2023 — Stalking Provision",
        summary=(
            "Covers following a person and repeatedly contacting them, or "
            "monitoring their internet/email/electronic communication use, "
            "despite clear disinterest. Punishable on first conviction with "
            "up to 3 years imprisonment and fine; up to 5 years on repeat "
            "conviction. (Successor provision to IPC Section 354D.)"
        ),
        applicable_when=[
            "Someone is repeatedly contacting you despite you asking them to stop",
            "Someone is monitoring your social media or online activity obsessively",
            "Physical following combined with online contact",
        ],
        source_note="Public summary of BNS 2023 stalking provision (successor to IPC 354D). Verify exact section number against current gazette text.",
        keywords=["stalking", "following", "repeated contact", "monitoring"],
    ),
    LegalDoc(
        id="bns_criminal_intimidation",
        category="blackmail_threats",
        title="Bharatiya Nyaya Sanhita (BNS) 2023 — Criminal Intimidation",
        summary=(
            "Covers threatening a person with injury to their person, "
            "reputation, or property, with intent to cause alarm or to force "
            "them to act against their will. Includes threats made via "
            "electronic communication. Punishment varies by severity of threat, "
            "up to 7 years for threats of death or grievous hurt."
        ),
        applicable_when=[
            "Someone is threatening to harm you or your reputation",
            "Someone is demanding money or actions under threat of exposure",
            "Blackmail involving photos, videos, or personal information",
        ],
        source_note="Public summary of BNS 2023 criminal intimidation provision (successor to IPC 503/506). Verify exact section number against current gazette text.",
        keywords=["blackmail", "threat", "extortion", "intimidation", "demand money"],
    ),
    LegalDoc(
        id="it_act_66",
        category="hacking",
        title="IT Act 2000 — Section 66 (Computer Related Offences)",
        summary=(
            "Covers dishonestly or fraudulently accessing a computer resource "
            "without authorization, or causing damage to data. Punishable with "
            "imprisonment up to 3 years or fine up to ₹5 lakh, or both."
        ),
        applicable_when=[
            "Your account was accessed without your permission",
            "Your device or data was tampered with by someone else",
            "Unauthorized access to email, social media, or other accounts",
        ],
        source_note="Public summary of IT Act 2000, Section 66 read with Section 43.",
        keywords=["hacking", "unauthorized access", "account compromise", "data breach"],
    ),
    LegalDoc(
        id="financial_fraud_general",
        category="financial_fraud",
        title="Financial Cyber Fraud — Reporting Pathway",
        summary=(
            "Financial fraud (UPI scams, fake investment schemes, phishing for "
            "banking details) should be reported immediately to the National "
            "Cybercrime Helpline (1930) for the best chance of transaction "
            "reversal, in addition to filing a complaint at cybercrime.gov.in. "
            "Speed matters significantly for fund recovery in these cases."
        ),
        applicable_when=[
            "Money was fraudulently transferred from your account",
            "You were tricked into sharing OTP, PIN, or banking credentials",
            "Fake investment or loan app scam",
        ],
        source_note="Public guidance from Indian Cyber Crime Coordination Centre (I4C) on financial fraud reporting.",
        keywords=["financial fraud", "upi scam", "otp", "bank fraud", "phishing", "money lost"],
    ),
]

HELPLINES = [
    {"name": "National Cyber Crime Helpline", "number": "1930", "use_for": "All cybercrime including financial fraud — call immediately for financial fraud"},
    {"name": "National Cyber Crime Reporting Portal", "number": "cybercrime.gov.in", "use_for": "Filing formal complaints online, anonymous reporting available"},
    {"name": "Women Helpline", "number": "181", "use_for": "Harassment, abuse, or threats against women"},
    {"name": "Childline India", "number": "1098", "use_for": "Any case involving a minor"},
    {"name": "Police Emergency", "number": "112", "use_for": "Immediate physical danger or credible threat of violence"},
    {"name": "iCall Psychosocial Helpline", "number": "9152987821", "use_for": "Emotional support and counseling related to the incident"},
]


def search_kb(query: str, category: str = None, top_k: int = 3) -> List[LegalDoc]:
    """
    Simple keyword-overlap retrieval (no external embedding API required,
    so this works even before a paid API key is added).
    Swap this for a real vector search later if desired — see README.
    """
    query_lower = query.lower()
    scored = []
    for doc in LEGAL_KNOWLEDGE_BASE:
        if category and doc.category != category:
            continue
        score = sum(1 for kw in doc.keywords if kw in query_lower)
        score += sum(1 for w in doc.applicable_when if any(tok in query_lower for tok in w.lower().split()))
        if score > 0:
            scored.append((score, doc))
    scored.sort(key=lambda x: -x[0])
    return [doc for _, doc in scored[:top_k]]
