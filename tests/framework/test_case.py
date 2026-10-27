"""
Base TestCase definition for Launcher3 testing framework.
"""

from typing import Callable, Optional, Dict, Any, List

class PreconditionError(Exception):
    """Raised during test setup when a required environment or UI state cannot be established."""
    pass

class TestCase:
    def __init__(
        self,
        test_id: str,
        category: str,
        name: str,
        description: str,
        expected_behavior: str,
        action: Callable[['LauncherDevice', 'UIInspector'], None],
        setup: Optional[Callable[['LauncherDevice', 'UIInspector'], None]] = None,
        teardown: Optional[Callable[['LauncherDevice', 'UIInspector'], None]] = None,
    ):
        self.id = test_id
        self.category = category
        self.name = name
        self.description = description
        self.expected_behavior = expected_behavior
        self.action = action
        self.setup = setup
        self.teardown = teardown
        
        # Execution status & details (PASS, FAIL, ERROR, SKIP)
        self.status: str = "PENDING"
        self.status_reason: str = ""
        self.pre_elements: List[Dict[str, Any]] = []
        self.post_elements: List[Dict[str, Any]] = []
        self.log_proof: str = ""
        self.duration_sec: float = 0.0
        self.error_trace: Optional[str] = None
        self.truncated_logs: bool = False

    def to_evaluation_payload(self) -> Dict[str, Any]:
        """Serializes the test execution and verification proof into an evaluation payload."""
        return {
            "id": self.id,
            "category": self.category,
            "name": self.name,
            "description": self.description,
            "expected_behavior": self.expected_behavior,
            "status": self.status,
            "status_reason": self.status_reason,
            "pre_elements_count": len(self.pre_elements),
            "post_elements_count": len(self.post_elements),
            "log_proof": self.log_proof,
            "truncated_logs": self.truncated_logs,
            "duration_sec": round(self.duration_sec, 3),
            "error": self.error_trace
        }
