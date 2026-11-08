#!/usr/bin/env python3
"""Translate a local card dispatch into native Codex SessionStart context."""
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[3]


def session_start(event):
    if event.get("hook_event_name") != "SessionStart" or ROOT.name != "forcitis-one-psa-codex-lab":
        return {}
    if not isinstance(event.get("cwd"), str):
        return {}
    try:
        lab, run, area = Path(event["cwd"]).resolve().relative_to(ROOT).parts
    except ValueError:
        return {}
    if lab != ".codex-lab" or not re.fullmatch(r"[a-z][a-z0-9-]{0,39}", run):
        return {}
    if area not in {"work", "review", "next"}:
        return {}
    dispatch = ROOT / lab / run / f"{area}-dispatch.json"
    if not dispatch.is_file() or dispatch.is_symlink():
        return {}
    task = json.loads(dispatch.read_text())
    message = f"PSA lab | {run} | {task['role']}"
    return {
        "systemMessage": message,
        "hookSpecificOutput": {
            "hookEventName": "SessionStart",
            "additionalContext": (
                f"{message} ({task['role_kind']}). {task['task']} "
                "This is a synthetic card. Keep all operations in this lab and use no GitHub or shared PSA state."
            ),
        },
    }


if __name__ == "__main__":
    print(json.dumps(session_start(json.load(sys.stdin))))
