"""
Launcher3 Automated Testing Framework.
Supports uiautomator2 execution, real-time DragTrace capture, and deterministic assertions.
"""

from .device import LauncherDevice
from .tracer import DragTracer
from .ui_inspector import UIInspector
from .test_case import TestCase, PreconditionError
from .runner import TestRunner

__all__ = [
    "LauncherDevice",
    "DragTracer",
    "UIInspector",
    "TestCase",
    "PreconditionError",
    "TestRunner",
]
