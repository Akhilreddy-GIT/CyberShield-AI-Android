"""
Risk Assessment Engine.

Rule-based scoring so it's transparent, auditable, and works without an LLM
API key. Each signal adds points; thresholds map to a risk band.
This is deliberately explainable (not a black-box LLM judgment) because a
risk level here can direct someone toward emergency services — it must be
possible to show *why* a case was scored the way it was.
"""

from dataclasses import dataclass, field
from typing import List
from enum import Enum


class RiskLevel(str, Enum):
    LOW = "Low Risk"
    MEDIUM = "Medium Risk"
    HIGH = "High Risk"
    CRITICAL = "Critical Emergency"


@dataclass
class RiskFactors:
    is_repeated_incident: bool = False
    involves_threat_of_harm: bool = False
    involves_minor: bool = False
    involves_financial_loss: bool = False
    involves_explicit_content: bool = False
    accused_knows_victim_location: bool = False
    ongoing_blackmail: bool = False
    victim_reports_feeling_unsafe: bool = False
    has_evidence: bool = False


@dataclass
class RiskAssessment:
    level: RiskLevel
    score: int
    triggered_factors: List[str] = field(default_factory=list)
    explanation: str = ""


def assess_risk(factors: RiskFactors) -> RiskAssessment:
    score = 0
    triggered = []

    # Critical-tier signals - any ONE of these alone should push toward critical
    if factors.involves_minor:
        score += 10
        triggered.append("Case involves a minor")
    if factors.involves_threat_of_harm:
        score += 8
        triggered.append("Credible threat of physical harm")
    if factors.accused_knows_victim_location:
        score += 6
        triggered.append("Accused has knowledge of victim's physical location")
    if factors.victim_reports_feeling_unsafe:
        score += 5
        triggered.append("Victim reports feeling immediately unsafe")

    # High-tier signals
    if factors.ongoing_blackmail:
        score += 5
        triggered.append("Ongoing blackmail/extortion")
    if factors.involves_explicit_content:
        score += 5
        triggered.append("Involves non-consensual explicit content")
    if factors.is_repeated_incident:
        score += 3
        triggered.append("Repeated/pattern of incidents")

    # Medium-tier signals
    if factors.involves_financial_loss:
        score += 3
        triggered.append("Financial loss occurred")
    if not factors.has_evidence:
        score += 1
        triggered.append("No evidence collected yet (increases uncertainty)")

    if score >= 10:
        level = RiskLevel.CRITICAL
    elif score >= 7:
        level = RiskLevel.HIGH
    elif score >= 3:
        level = RiskLevel.MEDIUM
    else:
        level = RiskLevel.LOW

    explanation = (
        f"Risk score {score}/10+ based on {len(triggered)} factor(s). "
        f"This is an automated first-pass triage, not a substitute for professional judgment."
    )

    return RiskAssessment(level=level, score=score, triggered_factors=triggered, explanation=explanation)
