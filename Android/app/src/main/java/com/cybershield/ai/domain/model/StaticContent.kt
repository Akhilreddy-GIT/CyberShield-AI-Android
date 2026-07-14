package com.cybershield.ai.domain.model

/**
 * Static reference content mirrored from backend `legal_kb.py` HELPLINES
 * and LEGAL_KNOWLEDGE_BASE — there is no list endpoint; these screens are
 * client-side education surfaces using the same source data.
 */
data class Helpline(
    val name: String,
    val number: String,
    val useFor: String,
)

data class LegalArticle(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val sourceNote: String,
)

object StaticContent {
    val helplines = listOf(
        Helpline("National Cyber Crime Helpline", "1930", "All cybercrime including financial fraud — call immediately for financial fraud"),
        Helpline("National Cyber Crime Reporting Portal", "cybercrime.gov.in", "Filing formal complaints online, anonymous reporting available"),
        Helpline("Women Helpline", "181", "Harassment, abuse, or threats against women"),
        Helpline("Childline India", "1098", "Any case involving a minor"),
        Helpline("Police Emergency", "112", "Immediate physical danger or credible threat of violence"),
        Helpline("iCall Psychosocial Helpline", "9152987821", "Emotional support and counseling related to the incident"),
    )

    val legalArticles = listOf(
        LegalArticle(
            "it_act_66c", "identity_theft",
            "IT Act 2000 — Section 66C (Identity Theft)",
            "Covers fraudulent or dishonest use of another person's identifying information such as electronic signatures, passwords, or other unique identification features. Punishable with imprisonment up to 3 years and a fine up to ₹1 lakh.",
            "Public summary of IT Act 2000, Section 66C (as amended 2008).",
        ),
        LegalArticle(
            "it_act_66d", "impersonation_fraud",
            "IT Act 2000 — Section 66D (Cheating by Personation using Computer Resource)",
            "Covers cheating someone by pretending to be another person using a computer resource or communication device. Punishable with imprisonment up to 3 years and a fine up to ₹1 lakh.",
            "Public summary of IT Act 2000, Section 66D.",
        ),
        LegalArticle(
            "it_act_66e", "privacy_violation",
            "IT Act 2000 — Section 66E (Violation of Privacy)",
            "Covers capturing, publishing, or transmitting images of a person's private parts or private acts without consent. Punishable with imprisonment up to 3 years or a fine up to ₹2 lakh, or both.",
            "Public summary of IT Act 2000, Section 66E.",
        ),
        LegalArticle(
            "it_act_67", "obscene_content",
            "IT Act 2000 — Section 67 (Publishing Obscene Material)",
            "Covers publishing or transmitting obscene material in electronic form. First conviction: up to 3 years imprisonment and fine up to ₹5 lakh.",
            "Public summary of IT Act 2000, Section 67.",
        ),
        LegalArticle(
            "it_act_67a", "sexually_explicit_content",
            "IT Act 2000 — Section 67A (Sexually Explicit Act Material)",
            "Covers publishing or transmitting material containing sexually explicit acts in electronic form. First conviction: up to 5 years imprisonment and fine up to ₹10 lakh.",
            "Public summary of IT Act 2000, Section 67A.",
        ),
        LegalArticle(
            "it_act_67b", "child_exploitation",
            "IT Act 2000 — Section 67B (Child Sexual Abuse Material)",
            "Covers publishing, browsing, downloading, or transmitting material depicting children in a sexually explicit manner. Treated as a CRITICAL emergency category.",
            "Public summary of IT Act 2000, Section 67B. Also covered under POCSO Act 2012.",
        ),
        LegalArticle(
            "bns_stalking", "stalking",
            "Bharatiya Nyaya Sanhita (BNS) 2023 — Stalking Provision",
            "Covers following a person and repeatedly contacting them, or monitoring their electronic communication use, despite clear disinterest.",
            "Public summary of BNS 2023 stalking provision (successor to IPC 354D).",
        ),
        LegalArticle(
            "bns_criminal_intimidation", "blackmail_threats",
            "Bharatiya Nyaya Sanhita (BNS) 2023 — Criminal Intimidation",
            "Covers threatening a person with injury to their person, reputation, or property, including threats made via electronic communication.",
            "Public summary of BNS 2023 criminal intimidation provision (successor to IPC 503/506).",
        ),
        LegalArticle(
            "it_act_66", "hacking",
            "IT Act 2000 — Section 66 (Computer Related Offences)",
            "Covers dishonestly or fraudulently accessing a computer resource without authorization, or causing damage to data.",
            "Public summary of IT Act 2000, Section 66 read with Section 43.",
        ),
        LegalArticle(
            "financial_fraud_general", "financial_fraud",
            "Financial Cyber Fraud — Reporting Pathway",
            "Financial fraud should be reported immediately to the National Cybercrime Helpline (1930) for the best chance of transaction reversal, plus cybercrime.gov.in.",
            "Public guidance from Indian Cyber Crime Coordination Centre (I4C).",
        ),
    )

    val awarenessTips = listOf(
        "Never share OTP, PIN, or banking passwords — banks never ask for them over call or chat.",
        "Preserve evidence: take screenshots before blocking anyone, and do not delete distressing messages.",
        "For UPI / bank fraud, call 1930 immediately — speed matters for fund recovery.",
        "Use strong, unique passwords and enable 2FA on email and social accounts.",
        "Think before you click: phishing links often mimic legitimate brands closely.",
        "If someone threatens to share intimate images, do not pay — contact helplines and preserve chats.",
        "Report fake profiles and harassment on-platform and at cybercrime.gov.in.",
        "This tool is anonymous-first. You do not need an account to get help.",
    )
}
