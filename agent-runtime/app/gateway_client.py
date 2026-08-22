import os
import httpx

class SecurityPlaneClient:
    def __init__(self) -> None:
        self.base_url = os.getenv("SPRING_GATEWAY_URL", "http://127.0.0.1:8080")
        self.token = os.getenv("AGENT_RUNTIME_TOKEN", "local-agent-runtime-token-change-me")
        self.timeout = float(os.getenv("AGENT_GATEWAY_TIMEOUT_SECONDS", "60"))

    def _headers(self) -> dict[str, str]:
        return {"X-Agent-Runtime-Token": self.token}

    def context(self, run_id: str) -> dict:
        return self._request("GET", f"/internal/agent/runs/{run_id}/context")

    def inference(self, run_id: str, prompt: str) -> str:
        return self._request("POST", f"/internal/agent/runs/{run_id}/inference", {"prompt": prompt})["content"]

    def propose(self, run_id: str, tool_name: str, arguments: dict) -> dict:
        return self._request("POST", f"/internal/agent/runs/{run_id}/tools/propose",
                             {"toolName": tool_name, "arguments": arguments})

    def execute_approved(self, approval_id: str) -> dict:
        return self._request("POST", f"/internal/agent/approvals/{approval_id}/execute", {})

    def _request(self, method: str, path: str, body: dict | None = None) -> dict:
        # Internal localhost traffic must never traverse an ambient corporate proxy, which may
        # strip the authentication header or receive trusted execution metadata.
        with httpx.Client(timeout=self.timeout, trust_env=False) as client:
            response = client.request(method, self.base_url + path, headers=self._headers(), json=body)
            response.raise_for_status()
            return response.json()
