import sqlite3
from pathlib import Path
from typing import Literal

from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import END, START, StateGraph
from langgraph.types import Command, interrupt

from .gateway_client import SecurityPlaneClient
from .state import AgentState

TOOLS = [
    ("github.readFile", {"repository": "imvaibhavm/ai-iam-gateway", "repositoryUrl": "https://github.com/imvaibhavm/ai-iam-gateway", "path": "PR #382 diff", "pullRequest": 382}),
    ("github.searchCode", {"repository": "imvaibhavm/ai-iam-gateway", "repositoryUrl": "https://github.com/imvaibhavm/ai-iam-gateway", "query": "authorization tests", "pullRequest": 382}),
    ("github.mergePullRequest", {"repository": "imvaibhavm/ai-iam-gateway", "repositoryUrl": "https://github.com/imvaibhavm/ai-iam-gateway", "pullRequest": 382, "reason": "review succeeded"}),
]

class SecureAgentGraph:
    def __init__(self, client: SecurityPlaneClient, checkpoint_path: str) -> None:
        self.client = client
        path = Path(checkpoint_path)
        path.parent.mkdir(parents=True, exist_ok=True)
        connection = sqlite3.connect(path, check_same_thread=False)
        self.checkpointer = SqliteSaver(connection)
        builder = StateGraph(AgentState)
        builder.add_node("reason", self.reason)
        builder.add_node("authorize_tool", self.authorize_tool)
        builder.add_node("approval", self.approval)
        builder.add_edge(START, "reason")
        builder.add_conditional_edges("reason", self.after_reason, {"tool": "authorize_tool", "end": END})
        builder.add_conditional_edges("authorize_tool", self.after_authorize,
                                      {"reason": "reason", "approval": "approval", "end": END})
        builder.add_edge("approval", "reason")
        self.graph = builder.compile(checkpointer=self.checkpointer)

    def reason(self, state: AgentState) -> dict:
        if state.get("stepCount", 0) >= state["maxSteps"]:
            return {"status": "STEP_BUDGET_EXCEEDED", "response": "Agent stopped at its step budget.", "pendingToolCall": None}
        cursor = state.get("toolCursor", 0)
        updates: dict = {"stepCount": state.get("stepCount", 0) + 1}
        if not state.get("planned"):
            # The model is reached only through Spring's policy-aware router. Its text cannot authorize actions.
            plan = self.client.inference(state["securityContext"]["runId"],
                "Create a concise review plan. Do not claim authorization and do not include secrets.")
            updates.update({"planned": True, "messages": state.get("messages", []) + [{"role": "assistant", "content": plan}]})
        if cursor < len(TOOLS):
            name, arguments = TOOLS[cursor]
            updates["pendingToolCall"] = {"name": name, "arguments": arguments}
            updates["status"] = "RUNNING"
            return updates
        final = self.client.inference(state["securityContext"]["runId"],
            "Summarize that the deterministic PR review workflow completed. Do not reveal sensitive data.")
        updates.update({"pendingToolCall": None, "status": "COMPLETED", "response": final})
        return updates

    def authorize_tool(self, state: AgentState) -> dict:
        call = state["pendingToolCall"]
        result = self.client.propose(state["securityContext"]["runId"], call["name"], call["arguments"])
        trajectory = state.get("trajectory", []) + [call["name"]]
        if result["decision"] == "DENY":
            return {"trajectory": trajectory, "toolCursor": state.get("toolCursor", 0) + 1,
                    "pendingToolCall": None, "toolResults": state.get("toolResults", []) + ["DENY:" + result["reason"]]}
        if result["decision"] == "REQUIRE_APPROVAL":
            return {"trajectory": trajectory, "approvalState": result, "status": "WAITING_APPROVAL"}
        return {"trajectory": trajectory, "toolCursor": state.get("toolCursor", 0) + 1,
                "pendingToolCall": None, "toolResults": state.get("toolResults", []) + [str(result.get("result"))]}

    def approval(self, state: AgentState) -> dict:
        approval = state["approvalState"]
        approved = interrupt({"approvalId": approval["approvalId"], "tool": state["pendingToolCall"]["name"],
                              "risk": "HIGH", "reason": approval["reason"]})
        if not approved:
            return {"status": "REJECTED", "response": "The high-risk action was rejected.",
                    "pendingToolCall": None, "toolCursor": len(TOOLS)}
        # Spring verifies persisted approval and re-runs authorization immediately before execution.
        result = self.client.execute_approved(approval["approvalId"])
        return {"status": "RUNNING", "approvalState": {**approval, "status": "APPROVED"},
                "pendingToolCall": None, "toolCursor": state.get("toolCursor", 0) + 1,
                "toolResults": state.get("toolResults", []) + [str(result.get("result"))]}

    @staticmethod
    def after_reason(state: AgentState) -> Literal["tool", "end"]:
        return "tool" if state.get("pendingToolCall") else "end"

    @staticmethod
    def after_authorize(state: AgentState) -> Literal["reason", "approval", "end"]:
        if state.get("status") == "WAITING_APPROVAL": return "approval"
        if state.get("status") in {"REJECTED", "STEP_BUDGET_EXCEEDED"}: return "end"
        return "reason"

    def start(self, run_id: str) -> dict:
        context = self.client.context(run_id)
        initial: AgentState = {**context, "messages": [{"role": "user", "content": "[content held by security plane]"}],
            "pendingToolCall": None, "approvalState": None, "securityContext": {"runId": run_id},
            "toolCursor": 0, "trajectory": [], "toolResults": [], "status": "RUNNING", "planned": False}
        return self._result(self.graph.invoke(initial, config=self._config(run_id)))

    def resume(self, run_id: str, approved: bool) -> dict:
        return self._result(self.graph.invoke(Command(resume=approved), config=self._config(run_id)))

    @staticmethod
    def _config(run_id: str) -> dict:
        return {"configurable": {"thread_id": run_id}, "metadata": {"request_id": run_id}}

    @staticmethod
    def _result(state: dict) -> dict:
        if state.get("__interrupt__"):
            return {"status": "WAITING_APPROVAL", "response": None}
        return {"status": state.get("status", "FAILED"), "response": state.get("response"),
                "trajectory": state.get("trajectory", [])}
