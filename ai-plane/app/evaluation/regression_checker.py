"""
CI / Production Regression Gate Checker.
Enforces minimum quality thresholds and flags regressions between workflow candidate and baseline.
"""
from typing import Dict, Any, List

DEFAULT_THRESHOLDS = {
    "min_overall_score": 0.75,
    "min_intent_accuracy": 0.80,
    "min_financial_risk_accuracy": 0.80,
    "min_routing_accuracy": 0.80,
    "min_citation_groundedness": 0.75,
}

class RegressionChecker:
    """Validates benchmark runs against quality thresholds."""

    @staticmethod
    def check_thresholds(
        eval_result: Dict[str, Any],
        thresholds: Dict[str, float] = DEFAULT_THRESHOLDS,
    ) -> Dict[str, Any]:
        violations: List[str] = []
        metrics = eval_result.get("metrics", {})
        overall_score = eval_result.get("overall_score", 0.0)

        if overall_score < thresholds["min_overall_score"]:
            violations.append(
                f"Overall score {overall_score} < threshold {thresholds['min_overall_score']}"
            )

        for metric_name, min_val in thresholds.items():
            if metric_name == "min_overall_score":
                continue
            key = metric_name.replace("min_", "")
            actual_val = metrics.get(key, 0.0)
            if actual_val < min_val:
                violations.append(f"Metric {key} ({actual_val}) < threshold ({min_val})")

        return {
            "passed": len(violations) == 0,
            "violations": violations,
            "thresholds": thresholds,
            "workflow_version": eval_result.get("workflow_version"),
        }

    @staticmethod
    def compare_runs(
        candidate: Dict[str, Any],
        baseline: Dict[str, Any],
        max_regression_delta: float = 0.05,
    ) -> Dict[str, Any]:
        """Ensures candidate does not regress relative to baseline by more than delta."""
        cand_score = candidate.get("overall_score", 0.0)
        base_score = baseline.get("overall_score", 0.0)

        diff = cand_score - base_score
        is_regression = diff < -max_regression_delta

        return {
            "candidate_version": candidate.get("workflow_version"),
            "baseline_version": baseline.get("workflow_version"),
            "candidate_score": cand_score,
            "baseline_score": base_score,
            "delta": round(diff, 4),
            "is_regression": is_regression,
            "approved": not is_regression,
        }
