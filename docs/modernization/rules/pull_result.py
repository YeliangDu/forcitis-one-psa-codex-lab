#!/usr/bin/env python3
"""Extract a finished subagent's final text from its JSONL transcript into a file (no echo).
usage: pull_result.py <output_file.jsonl> <dest.md>"""
import json, sys
last = None
for line in open(sys.argv[1], encoding="utf-8", errors="ignore"):
    try: o = json.loads(line)
    except Exception: continue
    m = o.get("message") or {}
    if (o.get("type") == "assistant" or m.get("role") == "assistant"):
        c = m.get("content")
        txt = "".join(p.get("text", "") for p in c if isinstance(p, dict) and p.get("type") == "text") if isinstance(c, list) else (c if isinstance(c, str) else "")
        if txt.strip(): last = txt
open(sys.argv[2], "w").write(last or "")
print(sys.argv[2], len(last or ""), "chars,", (last or "").count("### RULE-"), "rules")
