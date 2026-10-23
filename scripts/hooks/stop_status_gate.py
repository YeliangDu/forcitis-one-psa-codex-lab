#!/usr/bin/env python3
"""Stop gate: a session's last message of a turn declares its status, and a hand-over to the coordinator is really sent (GOV-1023-QUA-01).

Not a hook entry of its own: context_handoff.py (the one Stop entry in .claude/settings.json) calls `gate()` first on every Stop.

The last non-empty line of the turn's final assistant message must be `状态：继续｜待命｜等调度｜等用户｜完成点` (a separator and a short note may follow).
- Missing or misspelt: block once with the exact form; `stop_hook_active` lets the stop through and warns on stderr.
- `等调度` / `完成点`: the turn must hold a send to the coordinator (ccd send_message `session_id` or SendMessage `to`, an id or name from node_handoff),
  or an outbox line written this turn (`<psa home>/outbox/<session>.md`, SESSION_PLAYBOOK §11: throttled sends count as sent). Otherwise block once.
- `等调度` / `完成点` also always land in the file mailbox `<psa home>/mailbox/<time>-<session>.md` (final message cut to 4 KB, secrets scrubbed, written tmp then renamed),
  whether or not a message was sent, so the coordinator can fetch it with scripts/coord/mailbox.py when a send was lost.
- The coordinator itself (name or session id) and sub-agents are exempt. An unreadable transcript or a crash warns on stderr and never blocks a stop.
Usage: stop_status_gate.py --self-test
"""
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from handoff_snapshot import safe as scrub  # noqa: E402  (the same line-level secret rules the hand-over snapshots use)

STATUSES = ("继续", "待命", "等调度", "等用户", "完成点")
NEEDS_REPORT = ("等调度", "完成点")
LINE = re.compile(r"^\s*状态[:：]\s*(" + "|".join(STATUSES) + r")(?:\s*[。.]?\s*$|\s*[，,、；;:：（(—–\-｜|。.]\s*\S.*$|\s+\S.*$)")
MARKDOWN_PREFIX = re.compile(r"^[\s>*_#\-]+")
FORM = "状态：" + "｜".join(STATUSES)
MESSAGE_TOOLS = ("send_message", "SendMessage")
TAIL_BYTES = 4 * 1024 * 1024
MAILBOX_CHARS = 4096


def psa_home():
    return os.environ.get("MODERNIZE_PSA_HOME") or os.path.expanduser("~/.modernize/psa")


def read_lines(path):
    with open(path, "rb") as stream:
        size = os.fstat(stream.fileno()).st_size
        stream.seek(max(0, size - TAIL_BYTES))
        raw = stream.read().split(b"\n")
    entries = []
    for line in raw:
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if isinstance(entry, dict) and not entry.get("isSidechain"):
            entries.append(entry)
    return entries


def blocks(entry):
    message = entry.get("message")
    content = message.get("content") if isinstance(message, dict) else None
    if isinstance(content, str):
        return [{"type": "text", "text": content}]
    return [b for b in content if isinstance(b, dict)] if isinstance(content, list) else []


def is_user_turn_start(entry):
    """A real new turn: a user prompt or a message from another session. Not tool results, not what a tool injected (a Skill's text carries sourceToolUseID),
    not the Stop hook's own block feedback (same turn continued)."""
    if entry.get("type") != "user" or entry.get("sourceToolUseID") or not any(b.get("type") != "tool_result" for b in blocks(entry)):
        return False
    text = "".join(str(b.get("text", "")) for b in blocks(entry) if b.get("type") == "text")
    return not (entry.get("isMeta") and text.lstrip().startswith("Stop hook feedback"))


def parse_time(text):
    try:
        return datetime.datetime.fromisoformat(str(text).replace("Z", "+00:00")).timestamp()
    except ValueError:
        return None


def turn_of(entries):
    """(final assistant text, [(tool name, tool input)] of the turn, turn start epoch or None)."""
    start = 0
    for index in range(len(entries) - 1, -1, -1):
        if is_user_turn_start(entries[index]):
            start = index
            break
    turn = entries[start:]
    started = parse_time(turn[0].get("timestamp")) if turn and turn[0].get("type") == "user" else None
    tools, final = [], ""
    for entry in turn:
        if entry.get("type") != "assistant":
            continue
        texts = [b.get("text", "") for b in blocks(entry) if b.get("type") == "text"]
        if any(t.strip() for t in texts):
            final = "\n".join(texts)
        tools += [(str(b.get("name") or ""), b.get("input") if isinstance(b.get("input"), dict) else {}) for b in blocks(entry) if b.get("type") == "tool_use"]
    return final, tools, started


def status_of(text):
    """The declared status (one of STATUSES) from the last non-empty line, or None."""
    lines = [line for line in text.splitlines() if line.strip()]
    last = MARKDOWN_PREFIX.sub("", lines[-1].replace("**", "")) if lines else ""
    match = LINE.match(last)
    return match.group(1) if match else None


def target_of(value):
    return re.sub(r"\s*\[[^\]]*\]\s*$", "", value) if isinstance(value, str) else value  # `one-coordinator [3fa9c1]` is the name plus a disambiguating ref


def sent_to_coordinator(tools, targets):
    for name, tool_input in tools:
        if name.endswith(MESSAGE_TOOLS) and (target_of(tool_input.get("session_id")) in targets or target_of(tool_input.get("to")) in targets):
            return True
    return False


def outbox_written(home, name, since, now):
    path = os.path.join(home, "outbox", f"{name}.md")
    try:
        return os.path.getmtime(path) >= (since if since is not None else now - 600)
    except OSError:
        return False


def branch_of(root):
    result = subprocess.run(["git", "-C", root, "rev-parse", "--abbrev-ref", "HEAD"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return result.stdout.strip() if result.returncode == 0 else "?"


def write_mailbox(home, name, session_id, root, status, text, sent, now):
    directory = os.path.join(home, "mailbox")
    os.makedirs(directory, exist_ok=True)
    stamp = datetime.datetime.fromtimestamp(now).strftime("%Y%m%d-%H%M%S")
    body = scrub(text).strip()
    if len(body) > MAILBOX_CHARS:
        body = body[:MAILBOX_CHARS] + "\n…（已截断，全文见会话）"
    key = hashlib.sha1(f"{session_id}{text}".encode("utf-8")).hexdigest()[:6]
    final = os.path.join(directory, f"{stamp}-{name}-{key}.md")
    if os.path.exists(final):
        return final
    head = (f"---\n会话: {name}\n会话id: {session_id}\n分支: {branch_of(root)}\n时刻: "
            f"{datetime.datetime.fromtimestamp(now).isoformat(timespec='seconds')}\n状态: {status}\n已发调度: {'是' if sent else '否（信箱兜底）'}\n---\n")
    handle, temp = tempfile.mkstemp(dir=directory, prefix=".tmp-", suffix=".md")
    try:
        with os.fdopen(handle, "w", encoding="utf-8") as stream:
            stream.write(head + body + "\n")
        os.replace(temp, final)
    except BaseException:
        if os.path.exists(temp):
            os.unlink(temp)
        raise
    return final


def gate(payload, root, name, targets, now, home=None):
    """(hook output or None, [stderr warnings])."""
    home = home or psa_home()
    warnings = []
    if payload.get("agent_id") or not name or name in targets or payload.get("session_id") in targets:
        return None, warnings
    active = bool(payload.get("stop_hook_active"))
    try:
        entries = read_lines(payload.get("transcript_path"))
    except (OSError, TypeError) as error:
        return None, [f"stop_status_gate: 读不到本轮记录（{error}），状态声明与发送检查未做"]
    text, tools, started = turn_of(entries)
    if entries and entries[-1].get("isApiErrorMessage"):
        return None, [f"stop_status_gate: 本轮以系统合成的错误消息结束，不查状态声明（{name}）"]
    status = status_of(text)
    if status is None:
        reason = f"本轮最后一条消息的末行须是状态声明：{FORM}（可在状态后加一句说明；声明等调度或完成点的话，须先发消息给 one-coordinator）。补上后再结束。"
        if active:
            return None, [f"stop_status_gate: 缺状态声明，但已挡过一次，放行（{name}）"]
        return {"decision": "block", "reason": reason}, warnings
    if status in NEEDS_REPORT:
        sent = sent_to_coordinator(tools, targets) or outbox_written(home, name, started, now)
        if not sent and not active:
            return {"decision": "block", "reason": f"声明了「{status}」，但本轮没有发给 one-coordinator 的消息，也没有写 outbox。先发消息（限流失败就把要报的内容追加到 ~/.modernize/psa/outbox/{name}.md），再结束；信箱不能替代发消息。"}, warnings
        if not sent:
            warnings.append(f"stop_status_gate: 声明「{status}」但没有发给调度的消息，已挡过一次，放行；信箱兜底（{name}）")
        try:
            write_mailbox(home, name, payload.get("session_id") or "unknown", root, status, text, sent, now)
        except OSError as error:
            warnings.append(f"stop_status_gate: 信箱写不了（{error}），调度可能收不到这条")
    return None, warnings


# ---- self test ----

def line(kind, content, stamp=None):
    entry = {"type": kind, "message": {"content": content}}
    if stamp:
        entry["timestamp"] = stamp
    return entry


def tool_use(name, tool_input):
    return line("assistant", [{"type": "tool_use", "name": name, "input": tool_input}])


def say(text):
    return line("assistant", [{"type": "text", "text": text}])


def self_test():
    ids = ["local_coord", "one-coordinator"]
    with tempfile.TemporaryDirectory() as tmp:
        home = os.path.join(tmp, "psa")
        trans = os.path.join(tmp, "t.jsonl")
        subprocess.run(["git", "init", "-q", tmp], check=True, capture_output=True)

        def run(entries, active=False, name="one-test", session="s1", agent=None, now=1000.0, path=trans):
            with open(trans, "w", encoding="utf-8") as stream:
                stream.write("\n".join(json.dumps(e, ensure_ascii=False) for e in entries) + "\n")
            payload = {"transcript_path": path, "session_id": session, "stop_hook_active": active}
            if agent:
                payload["agent_id"] = agent
            return gate(payload, tmp, name, ids, now, home)

        def mailbox():
            directory = os.path.join(home, "mailbox")
            return sorted(f for f in os.listdir(directory) if f.endswith(".md") and not f.startswith(".")) if os.path.isdir(directory) else []

        ask = line("user", "做吧")
        send = tool_use("mcp__ccd_session_mgmt__send_message", {"session_id": "local_coord", "message": "done"})

        for bad in ("没有声明", "状态：等调渡", "状态: 等待", "状态：", "状态：继续吧", "这是状态：继续"):
            out, _ = run([ask, say(bad)])
            assert out and out["decision"] == "block" and FORM in out["reason"], f"{bad!r} must be blocked"
        out, warn = run([ask, say("没有声明")], active=True)
        assert out is None and warn and "放行" in warn[0], "stop_hook_active lets a missing declaration through, loudly"
        for ok in ("状态：继续", "状态：待命", "状态：等用户", "状态: 继续，等测试跑完", "结论如上。\n\n状态：等用户（要你定 B3）\n"):
            assert run([ask, say(ok)])[0] is None, f"{ok!r} passes"
        assert mailbox() == [], "继续/待命/等用户 write no mailbox entry"

        out, _ = run([ask, say("报告\n状态：等调度")])
        assert out and "没有发给 one-coordinator" in out["reason"] and mailbox() == [], "等调度 without a send is blocked and writes nothing"
        out, _ = run([ask, say("报告\n状态：完成点")])
        assert out and out["decision"] == "block", "完成点 without a send is blocked"
        out, _ = run([ask, tool_use("mcp__ccd_session_mgmt__send_message", {"session_id": "local_other", "message": "x"}), say("报告\n状态：等调度")])
        assert out and out["decision"] == "block", "a send to someone else is no report"
        out, _ = run([ask, tool_use("mcp__ccd_session_mgmt__send_message", {"session_id": "local_coord", "message": "x"}), line("user", [{"type": "tool_result", "content": "ok"}]),
                      say("报告\n状态：等调度")])
        assert out is None and len(mailbox()) == 1, "a send to the coordinator lets 等调度 through and lands in the mailbox"
        text = open(os.path.join(home, "mailbox", mailbox()[0]), encoding="utf-8").read()
        assert "会话: one-test" in text and "已发调度: 是" in text and "状态: 等调度" in text and "报告" in text, text
        assert run([ask, tool_use("SendMessage", {"to": "one-coordinator", "message": "x"}), say("好\n状态：完成点")])[0] is None, "SendMessage by name counts"
        # an old send does not count for a new turn
        assert run([ask, send, say("报告\n状态：等调度"), line("user", "再来"), say("又报告\n状态：等调度")])[0]["decision"] == "block", "a send from an earlier turn does not count"

        # outbox: written this turn counts as sent, an old one does not
        outbox = os.path.join(home, "outbox", "one-test.md")
        os.makedirs(os.path.dirname(outbox), exist_ok=True)
        open(outbox, "w").write("x\n")
        os.utime(outbox, (900, 900))
        turn = line("user", "做吧", "1970-01-01T00:16:40+00:00")  # epoch 1000
        assert run([turn, say("报告\n状态：等调度")], now=1100.0)[0]["decision"] == "block", "an outbox line older than the turn is not a send"
        os.utime(outbox, (1050, 1050))
        before = len(mailbox())
        assert run([turn, say("限流了\n状态：等调度")], now=1100.0)[0] is None and len(mailbox()) == before + 1, "an outbox line written this turn counts as sent"

        os.remove(outbox)
        # stop_hook_active with no send: let through with a warning, mailbox is the fallback
        before = len(mailbox())
        out, warn = run([ask, say("兜底报告\n状态：等调度")], active=True, session="s2", now=1200.0)
        assert out is None and warn and "信箱兜底" in warn[0] and len(mailbox()) == before + 1, "the mailbox catches a report that was never sent"
        assert "已发调度: 否" in open(os.path.join(home, "mailbox", mailbox()[-1]), encoding="utf-8").read()
        # the same message twice is one entry; secrets are scrubbed and the body is cut
        before = len(mailbox())
        run([ask, say("兜底报告\n状态：等调度")], active=True, session="s2", now=1200.0)
        assert len(mailbox()) == before, "same session and message, same second: one entry"
        run([ask, say("key=" + "s" + "k-" + "abcdefghijklmnop1234\n密" + "码和口" + "令：hunter33\npass" + "word: hunter22\ntoken：hunter44\n" + ("长" * 100 + "\n") * 50 + "状态：完成点")], active=True, session="s3", now=1300.0)
        last = open(os.path.join(home, "mailbox", mailbox()[-1]), encoding="utf-8").read()
        assert "hunter22" not in last and "hunter33" not in last and "hunter44" not in last and "[敏感内容已隐藏]" in last and ("s" + "k-" + "abcdefghijklmnop1234") not in last and "已截断" in last and len(last) < MAILBOX_CHARS + 400, "secrets scrubbed, body cut to 4 KB"
        assert not [f for f in os.listdir(os.path.join(home, "mailbox")) if f.startswith(".tmp-")], "no temp file left behind"

        # review fixes: markdown / punctuation forms pass, a tool-injected or Stop-feedback user line does not start a new turn, name+ref targets match
        assert status_of("x\n状态：完成点。") == "完成点" and status_of("**状态：完成点**") == "完成点" and status_of("状态：继续吧") is None
        for ok in ("**状态：继续**", "- 状态：待命", "> 状态：等用户", "状态：等用户｜要你定 B3"):
            assert run([ask, say(ok)])[0] is None, f"{ok!r} passes"
        assert run([ask, say("x\n状态：完成点。")], session="p1", now=1400.0)[0]["decision"] == "block", "完成点 with a full stop still needs the send"
        injected = dict(line("user", [{"type": "text", "text": "Base directory for this skill: /x"}]), isMeta=True, sourceToolUseID="toolu_1")
        assert run([ask, send, injected, say("报告\n状态：等调度")], session="p2", now=1500.0)[0] is None, "a Skill's injected user line is not a new turn"
        feedback = dict(line("user", [{"type": "text", "text": "Stop hook feedback:\n本轮最后一条消息的末行须是状态声明"}]), isMeta=True)
        n = len(mailbox())
        assert run([ask, send, say("报告"), feedback, say("报告\n状态：等调度")], session="p3", now=1600.0)[0] is None
        assert "已发调度: 是" in open(os.path.join(home, "mailbox", mailbox()[-1]), encoding="utf-8").read() and len(mailbox()) == n + 1, "Stop feedback continues the turn, the earlier send counts"
        other = dict(line("user", [{"type": "text", "text": "Another Claude session sent a message"}]), isMeta=True)
        assert run([ask, send, say("a"), other, say("报告\n状态：等调度")], session="p4", now=1700.0)[0]["decision"] == "block", "a message from another session starts a new turn"
        assert run([ask, tool_use("SendMessage", {"to": "one-coordinator [3fa9c1]", "message": "x"}), say("好\n状态：等调度")], session="p5", now=1800.0)[0] is None, "name plus ref matches"
        assert run([ask, dict(say("api error"), isApiErrorMessage=True)])[0] is None, "a synthetic error ending is not gated"

        # exemptions and failures
        assert run([ask, say("没声明")], name="one-coordinator")[0] is None, "the coordinator is exempt (by name)"
        assert run([ask, say("没声明")], session="local_coord")[0] is None, "the coordinator is exempt (by session id)"
        assert run([ask, say("没声明")], agent="a1")[0] is None, "sub-agents are exempt"
        assert run([ask, say("没声明")], name=None)[0] is None, "an unidentified session is not gated"
        out, warn = run([ask, say("x")], path=os.path.join(tmp, "missing.jsonl"))
        assert out is None and warn and "读不到" in warn[0], "an unreadable transcript warns, never blocks silently"
        out, warn = run([ask, say("x")], path=None)
        assert out is None and warn, "a missing transcript path warns too"
        # sidechain (sub-agent) messages do not count as the final message or as a send
        side = dict(say("子代理\n状态：继续"), isSidechain=True)
        assert run([ask, say("无声明"), side])[0]["decision"] == "block", "a sub-agent line is not the session's declaration"
    print("stop_status_gate self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if "--self-test" in sys.argv else 0)
