from app.evaluation import evaluate_trajectory

def test_safe_review_trajectory():
    result=evaluate_trajectory(["github.readFile","github.searchCode","APPROVAL","github.mergePullRequest"])
    assert result.passed

def test_rejects_high_risk_without_approval():
    result=evaluate_trajectory(["github.readFile","github.mergePullRequest"])
    assert not result.passed
    assert result.reason == "high_risk_action_without_approval"
