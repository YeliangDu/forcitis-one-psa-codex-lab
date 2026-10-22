"""Task id pattern shared by commit_msg_gate.py and bg_task_name_gate.py: the one in check_tasks.py (lanes and session codes), not a looser copy.

The id must not touch an ASCII letter or digit on either side; Chinese text may touch it (`按CI-1022-QUA-03的要求`).
"""
import importlib.util
import re
from pathlib import Path

CHECK_TASKS = Path(__file__).resolve().parents[2] / "docs" / "modernization" / "check_tasks.py"


def load_pattern():
    spec = importlib.util.spec_from_file_location("check_tasks_for_hooks", CHECK_TASKS)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.TASK_ID.pattern


TASK_ID_PATTERN = load_pattern()
TASK_ID = re.compile(r"(?<![A-Za-z0-9])" + TASK_ID_PATTERN + r"(?![A-Za-z0-9])")
