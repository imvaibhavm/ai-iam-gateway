import hmac
import os
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel

from .gateway_client import SecurityPlaneClient
from .graph import SecureAgentGraph

app = FastAPI(title="AI Security LangGraph Runtime", version="0.1.0")
graph = SecureAgentGraph(SecurityPlaneClient(), os.getenv("LANGGRAPH_CHECKPOINT_PATH", "data/checkpoints.sqlite"))

class Start(BaseModel): runId: str
class Resume(BaseModel): approved: bool

def authenticate(value: str | None) -> None:
    expected = os.getenv("AGENT_RUNTIME_TOKEN", "local-agent-runtime-token-change-me")
    if len(expected) < 24 or value is None or not hmac.compare_digest(value, expected):
        raise HTTPException(status_code=401, detail="agent_runtime_authentication_failed")

@app.get("/health")
def health() -> dict: return {"status": "UP"}

@app.post("/runs")
def start(request: Start, x_agent_runtime_token: str | None = Header(default=None)) -> dict:
    authenticate(x_agent_runtime_token)
    # Use the already authenticated inbound token for this server-to-server callback chain.
    # It is never persisted or added to graph state/telemetry.
    graph.client.token = x_agent_runtime_token
    return graph.start(request.runId)

@app.post("/runs/{run_id}/resume")
def resume(run_id: str, request: Resume, x_agent_runtime_token: str | None = Header(default=None)) -> dict:
    authenticate(x_agent_runtime_token)
    graph.client.token = x_agent_runtime_token
    return graph.resume(run_id, request.approved)
