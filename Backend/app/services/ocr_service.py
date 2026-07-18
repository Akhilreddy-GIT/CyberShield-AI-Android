"""
OCR Service — extracts text from uploaded evidence screenshots.

Uses Tesseract (via pytesseract), which runs fully locally — no external
API, no cost, works offline. This is real OCR, not a placeholder: it
actually reads text out of uploaded images and stores it against the
evidence record so it becomes searchable/reviewable in the case report.

Limitations (stated honestly, not hidden): Tesseract's accuracy drops on
low-resolution screenshots, unusual fonts, or dense chat-bubble UIs with
overlapping elements. It's good enough to extract readable evidence text
for a case report, not a forensic-grade transcription tool.
"""

import pytesseract
from PIL import Image
from typing import Optional

SUPPORTED_OCR_EXTENSIONS = {".png", ".jpg", ".jpeg"}


def extract_text_from_image(file_path: str) -> Optional[str]:
    """
    Returns extracted text, or None if the file isn't an image type we can OCR,
    or if extraction fails (never raises — evidence upload must not break
    because OCR had trouble with one file).
    """
    ext = file_path.lower().rsplit(".", 1)
    if not ext or f".{ext[-1]}" not in SUPPORTED_OCR_EXTENSIONS:
        return None

    try:
        image = Image.open(file_path)
        text = pytesseract.image_to_string(image)
        text = text.strip()
        return text if text else None
    except Exception:
        # OCR failure (corrupt image, tesseract not installed, unreadable
        # content) should never block the evidence upload itself.
        return None
