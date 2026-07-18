from dotenv import load_dotenv

load_dotenv()

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.models.db import init_db
from app.routers import chat, cases, evidence, report, auth, ws_case, guardian, crisis

app = FastAPI(
    title="CyberShield AI",
    description="AI-Powered Cyber Assistance Expert System — academic project",
    version="1.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # tighten before any real deployment
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat.router)
app.include_router(cases.router)
app.include_router(evidence.router)
app.include_router(report.router)
app.include_router(auth.router)
app.include_router(ws_case.router)
app.include_router(guardian.router)
app.include_router(crisis.router)


@app.on_event("startup")
def on_startup():
    init_db()


@app.get("/")
def root():
    return {
        "service": "CyberShield AI",
        "status": "running",
        "docs": "/docs",
    }


@app.get("/api/health")
def health():
    import os
    return {
        "status": "ok",
        "llm_configured": bool(os.getenv("GROQ_API_KEY")),
    }
