"""
Real-time case status updates via WebSockets.

A client connects to /ws/case/{case_id} and receives a push whenever that
case's status or risk level changes (e.g. risk assessment run, critical
escalation triggered in chat). This is genuinely real-time — not polling —
using an in-memory connection registry per case.

Scope note: this is a single-process in-memory broadcaster, which is
correct and sufficient for a single-server demo deployment. A
multi-instance production deployment would need a shared broker (e.g.
Redis pub/sub) to fan out across processes — noted here rather than
silently glossed over.
"""

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict, List
import json

router = APIRouter()

# case_id -> list of connected websockets
_connections: Dict[str, List[WebSocket]] = {}


@router.websocket("/ws/case/{case_id}")
async def case_status_socket(websocket: WebSocket, case_id: str):
    await websocket.accept()
    _connections.setdefault(case_id, []).append(websocket)
    try:
        while True:
            # We don't expect incoming messages, but keep the loop alive to
            # detect disconnects; ignore anything a client sends.
            await websocket.receive_text()
    except WebSocketDisconnect:
        pass
    finally:
        if websocket in _connections.get(case_id, []):
            _connections[case_id].remove(websocket)
        if case_id in _connections and not _connections[case_id]:
            del _connections[case_id]


async def broadcast_case_update(case_id: str, update: dict):
    """
    Call this after any change to a case's status/risk_level so connected
    clients get pushed the new state immediately. Safe to call even if no
    one is connected (no-op).
    """
    sockets = _connections.get(case_id, [])
    dead = []
    for ws in sockets:
        try:
            await ws.send_text(json.dumps(update))
        except Exception:
            dead.append(ws)
    for ws in dead:
        sockets.remove(ws)
