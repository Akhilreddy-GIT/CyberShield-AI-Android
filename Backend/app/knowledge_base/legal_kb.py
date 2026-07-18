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
from typing import List, Optional


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
    LegalDoc(
        id="sim_swap_fraud",
        category="financial_fraud",
        title="SIM Swap Fraud — Section 66C/66D + Telecom Guidance",
        summary=(
            "SIM swap fraud (attacker convinces or bribes a telecom outlet to "
            "issue a duplicate SIM, or tricks the victim into approving a swap) "
            "is prosecuted under IT Act Sections 66C (identity theft) and 66D "
            "(cheating by personation), often alongside banking fraud provisions "
            "once linked accounts are drained. Losing signal unexpectedly is the "
            "key early warning sign."
        ),
        applicable_when=[
            "Your SIM suddenly stopped working / shows 'no service' unexpectedly",
            "You received an SMS about a SIM swap or number port you didn't request",
            "Bank OTPs started going to a number you don't control",
        ],
        source_note="Public summary of IT Act 66C/66D as applied to SIM-swap fraud, plus TRAI/telecom-operator guidance on unauthorized SIM replacement.",
        keywords=["sim swap", "sim card stopped", "no service", "duplicate sim", "number port"],
    ),
    LegalDoc(
        id="ransomware_malware",
        category="hacking",
        title="Ransomware & Malware Attacks — IT Act Section 43 + Section 66",
        summary=(
            "Introducing ransomware, viruses, or malware, or damaging/encrypting "
            "data without authorization, is covered under IT Act Section 43 "
            "(civil penalty for damage to computer systems) and Section 66 "
            "(criminal offence when done dishonestly/fraudulently). Never pay "
            "the ransom as a first step — it does not guarantee data recovery "
            "and funds cybercriminal operations."
        ),
        applicable_when=[
            "Your files were encrypted and a ransom is being demanded",
            "Your device is infected with malware from a downloaded file or link",
            "A 'remote access' app was installed on your device by a scammer",
        ],
        source_note="Public summary of IT Act 2000, Sections 43 and 66, as applied to ransomware/malware incidents.",
        keywords=["ransomware", "malware", "virus", "encrypted files", "remote access app", "anydesk", "teamviewer"],
    ),
    LegalDoc(
        id="deepfake_ai_voice_scam",
        category="impersonation_fraud",
        title="Deepfake & AI Voice Cloning Scams — Section 66D + Section 66E/67",
        summary=(
            "AI-generated voice clones (e.g. a fake 'relative in distress' call) "
            "or deepfake video/images used to defraud or defame someone fall "
            "under IT Act Section 66D (cheating by personation) and, where "
            "explicit or private imagery is fabricated, Sections 66E/67/67A as "
            "well. This is a fast-evolving area — evidence preservation (call "
            "recordings, the original audio/video used to train the clone if "
            "known) is especially important."
        ),
        applicable_when=[
            "Someone used an AI-cloned voice of a relative/friend to demand money urgently",
            "A fake video or image ('deepfake') of you or someone you know is circulating",
        ],
        source_note="Public summary of IT Act 2000 Sections 66D, 66E, 67 as applied to deepfake/voice-clone fraud; a developing area of Indian cyber law.",
        keywords=["deepfake", "ai voice", "voice clone", "fake video", "cloned voice", "relative in trouble call"],
    ),
    LegalDoc(
        id="job_loan_parcel_scams",
        category="financial_fraud",
        title="Job Scams, Loan App Scams & Parcel/Courier Scams — Reporting Pathway",
        summary=(
            "Fake job offers demanding upfront 'registration fees', predatory "
            "loan apps that harass borrowers or leak contacts, and fake courier/"
            "parcel-detained-at-customs calls are all common financial fraud "
            "patterns. They should be reported to 1930 and cybercrime.gov.in "
            "the same as other financial fraud; loan-app harassment can "
            "additionally be reported to the RBI Sachet portal if the app is "
            "an unregistered lender."
        ),
        applicable_when=[
            "A 'job' asked for payment before hiring you, or required unusual advance tasks",
            "A loan app is threatening you, contacting your relatives, or charging illegal rates",
            "You got a call about a 'parcel' held at customs demanding a fee or your personal details",
        ],
        source_note="Public guidance from I4C on job/loan/parcel scam patterns; RBI Sachet portal for unregistered lending apps.",
        keywords=["job scam", "fake job", "loan app", "loan harassment", "parcel scam", "courier scam", "customs fee"],
    ),
    LegalDoc(
        id="qr_code_otp_scams",
        category="financial_fraud",
        title="QR Code & OTP/Screen-Share Scams — Reporting Pathway",
        summary=(
            "Scanning an unknown QR code to 'receive' money (QR codes only "
            "authorize outgoing payments, never incoming ones) or sharing an "
            "OTP/screen-sharing app with a caller claiming to be from a bank or "
            "customer care are among the most common ways UPI accounts are "
            "drained. No genuine bank or company will ever ask for your OTP "
            "over a call."
        ),
        applicable_when=[
            "You scanned a QR code from someone claiming to send you money and lost money instead",
            "You shared an OTP with someone on a phone call",
            "You installed a screen-sharing app at a caller's request",
        ],
        source_note="Public guidance from I4C / RBI on UPI QR-code and OTP-sharing fraud patterns.",
        keywords=["qr code scam", "otp scam", "screen share", "fake customer care", "customer care number", "receive money qr"],
    ),
    LegalDoc(
        id="crypto_investment_scam",
        category="financial_fraud",
        title="Crypto & Fake Investment Scams — BNS Cheating Provisions + IT Act 66D",
        summary=(
            "Fake trading platforms, crypto investment schemes promising "
            "guaranteed returns, and Ponzi-style 'investment groups' on "
            "WhatsApp/Telegram are prosecuted as cheating (BNS cheating "
            "provisions, successor to IPC 420) combined with IT Act 66D. Funds "
            "moved to crypto wallets are extremely hard to trace and recover — "
            "reporting speed still matters for the fiat on-ramp/off-ramp stage."
        ),
        applicable_when=[
            "You invested in a scheme promising guaranteed high returns and can't withdraw funds",
            "You were added to a WhatsApp/Telegram 'trading group' that pressured you to invest",
            "A crypto exchange or wallet linked to a scam holds your funds",
        ],
        source_note="Public guidance from I4C on investment/crypto fraud; general summary of BNS cheating provisions (successor to IPC 420).",
        keywords=["crypto scam", "fake investment", "trading app scam", "guaranteed returns", "ponzi", "telegram investment group"],
    ),
    LegalDoc(
        id="whatsapp_instagram_hacking",
        category="hacking",
        title="WhatsApp / Instagram / Social Media Account Hacking — Section 66/66C",
        summary=(
            "Unauthorized access to a WhatsApp, Instagram, Facebook, or other "
            "social media account — including via SIM swap, phishing link, or "
            "OTP theft — falls under IT Act Sections 66 and 66C. Platform-side "
            "recovery (in-app 'account hacked' reporting) should be started "
            "immediately alongside a police/1930 report, since fast recovery "
            "reduces the window for the attacker to message your contacts or "
            "post as you."
        ),
        applicable_when=[
            "Your WhatsApp/Instagram/Facebook account was accessed without your permission",
            "You lost access and the attacker changed your email/phone/password",
            "The attacker is messaging your contacts pretending to be you",
        ],
        source_note="Public summary of IT Act 2000 Sections 66/66C as applied to social-media account takeover; platform account-recovery flows (Meta, WhatsApp) are separate from but complementary to legal reporting.",
        keywords=["whatsapp hacked", "instagram hacked", "facebook hacked", "account takeover", "social media hacked", "lost access to account"],
    ),
    LegalDoc(
        id="bns_cyberbullying_harassment",
        category="cyberbullying",
        title="Bharatiya Nyaya Sanhita (BNS) 2023 — Online Harassment & Defamation Provisions",
        summary=(
            "Repeated mocking, humiliation, spreading rumors, or abusive messages "
            "online can fall under BNS provisions for criminal intimidation, "
            "insult intended to provoke breach of peace, and defamation "
            "(successor provisions to IPC 499/500/504), especially when "
            "repeated or coordinated by a group. School/college-based "
            "cyberbullying can also be raised with the institution's internal "
            "complaints or disciplinary committee alongside a police complaint."
        ),
        applicable_when=[
            "Someone is mocking, humiliating, or spreading rumors about you online",
            "You are receiving repeated mean or abusive comments/messages",
            "A group chat or social media post is being used to target and humiliate you",
        ],
        source_note="Public summary of BNS 2023 provisions on intimidation, insult, and defamation (successors to IPC 499/500/503/504) as applied to online harassment; no single dedicated 'cyberbullying' section exists in Indian law, so the applicable provision depends on the specific conduct.",
        keywords=["bully", "bullying", "mocking", "trolling", "mean comments", "spreading rumors", "humiliating me online", "group chat against me", "harass", "harassment", "unwanted messages", "abuse online"],
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

# Indexed by helpline "name" for O(1) lookup when building a filtered,
# incident-specific subset below — avoids re-typing each dict literal.
_HELPLINE_BY_NAME = {h["name"]: h for h in HELPLINES}

_CYBER_HELPLINE = _HELPLINE_BY_NAME["National Cyber Crime Helpline"]
_CYBER_PORTAL = _HELPLINE_BY_NAME["National Cyber Crime Reporting Portal"]
_WOMEN_HELPLINE = _HELPLINE_BY_NAME["Women Helpline"]
_CHILDLINE = _HELPLINE_BY_NAME["Childline India"]
_POLICE = _HELPLINE_BY_NAME["Police Emergency"]
_ICALL = _HELPLINE_BY_NAME["iCall Psychosocial Helpline"]

# Category -> ordered list of relevant helplines ONLY. This is the single
# source of truth for "which helplines does this incident need" — every
# category maps to a deliberately small, relevant subset rather than
# reusing the full HELPLINES list. cybercrime.gov.in + 1930 are almost
# always relevant (they're the national reporting pathway for any cyber
# incident), so they anchor every category; everything else is added only
# when it actually applies to that category.
HELPLINES_BY_CATEGORY = {
    "financial_fraud": [_CYBER_HELPLINE, _CYBER_PORTAL],
    "identity_theft": [_CYBER_HELPLINE, _CYBER_PORTAL],
    "hacking": [_CYBER_HELPLINE, _CYBER_PORTAL],
    "sexually_explicit_content": [_CYBER_HELPLINE, _CYBER_PORTAL, _POLICE, _WOMEN_HELPLINE],
    "blackmail_threats": [_CYBER_HELPLINE, _CYBER_PORTAL, _POLICE, _WOMEN_HELPLINE],
    "stalking": [_CYBER_HELPLINE, _CYBER_PORTAL, _POLICE, _WOMEN_HELPLINE],
    "impersonation_fraud": [_CYBER_HELPLINE, _CYBER_PORTAL],
    "child_exploitation": [_CHILDLINE, _POLICE, _CYBER_PORTAL],
    "privacy_violation": [_CYBER_HELPLINE, _CYBER_PORTAL],
    "cyberbullying": [_CYBER_HELPLINE, _CYBER_PORTAL],
    "online_harassment": [_CYBER_HELPLINE, _CYBER_PORTAL, _WOMEN_HELPLINE],
    "social_media_abuse": [_CYBER_HELPLINE, _CYBER_PORTAL],
    "other_cyber": [_CYBER_HELPLINE, _CYBER_PORTAL],
}

# Emergency-type overlays: layered on TOP of the category list (not a
# replacement), since an emergency type is about *urgency*, while category
# is about *what kind* of incident this is. Only adds a helpline that isn't
# already present. e.g. self-harm language on top of a financial-fraud case
# should still surface iCall, without dropping the financial helplines.
_EMERGENCY_TYPE_ADDITIONS = {
    "self_harm": [_ICALL],
    "imminent_danger": [_POLICE],
    "child_safety": [_CHILDLINE, _POLICE],
    "active_exploitation": [_POLICE],
    "account_takeover": [],
}


def get_relevant_helplines(category: Optional[str] = None, emergency_type: Optional[str] = None) -> List[dict]:
    """Returns ONLY the helplines relevant to this specific incident,
    instead of the full static HELPLINES list. category should be a
    CyberCategory value (e.g. "financial_fraud", "sexually_explicit_content");
    unrecognized/missing category falls back to the generic cybercrime
    pathway (1930 + cybercrime.gov.in) rather than everything.
    emergency_type (from IntentResult.emergency_type) can add one or two
    situational helplines on top, e.g. iCall for self-harm language,
    Childline for child-safety signals — without pulling in unrelated ones.
    """
    base = HELPLINES_BY_CATEGORY.get(category, [_CYBER_HELPLINE, _CYBER_PORTAL])
    result = list(base)
    seen_names = {h["name"] for h in result}
    for addition in _EMERGENCY_TYPE_ADDITIONS.get(emergency_type, []):
        if addition["name"] not in seen_names:
            result.append(addition)
            seen_names.add(addition["name"])
    return result


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
