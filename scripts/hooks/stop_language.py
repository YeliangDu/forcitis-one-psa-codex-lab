#!/usr/bin/python3
"""Language rule (CLAUDE.md baseline 6, #900): every session talks to the user in Chinese.

--stop           Stop hook. Reads the turn's last assistant message, drops code, URLs, paths and identifiers, and blocks the turn when the
                 remaining prose is mainly not Chinese. No model call, no network; fails open (any error: no output, exit 0).
--session-start  SessionStart (compact, resume): prints one line re-stating the rule, since a summary drops it.
--self-test      Red and green cases plus timing on a long message.
"""
import json
import re
import sys
import time

MIN_LETTERS = 40   # shorter prose (an acknowledgement, a one-line status) is not worth a rewrite
MIN_SHARE = 0.30   # CJK / (CJK + Latin letters of prose). One Chinese character carries about as much as two or three English letters, so a Chinese
                   # report with its English terms stays well above 0.3 (mixed reports measured 0.4 to 0.7); an English report with a Chinese heading is under 0.1.
REMINDER = "【语言】对用户的文字一律用中文（代码、命令、路径、标识符照原样）；给其他会话的消息和 agent 读的文件仍用英文（CLAUDE.md 基线 6）。"

FENCE = re.compile(r"(?m)^[ \t]*(```|~~~).*?(?:^[ \t]*\1[^\n]*$|\Z)", re.S)
INLINE = re.compile(r"`[^`\n]*`")
LINK_TARGET = re.compile(r"\]\([^)\s]*\)")
URL = re.compile(r"\b[a-zA-Z][a-zA-Z0-9+.-]*://\S+")
CJK = re.compile(r"[㐀-䶿一-鿿豈-﫿]")
EDGE = ".,;:!?\"'()[]{}<>*_“”‘’，。；：！？（）、"
WORD = re.compile(r"[A-Za-z]+")


def identifier_like(token):
    """A token that names code, not prose: digits, path or call punctuation inside, an inner capital, or all capitals."""
    t = token.strip(EDGE)
    if not t or not WORD.search(t):
        return True
    if any(c in t for c in "_/\\.:#@=<>()[]{}~|$%&+") or any(c.isdigit() for c in t):
        return True
    return t.isupper() or any(c.isupper() for c in t[1:])


def shares(text):
    """(CJK characters, Latin letters in prose tokens) of `text` once code-like material is removed."""
    text = FENCE.sub(" ", text)
    text = INLINE.sub(" ", text)
    text = LINK_TARGET.sub("] ", text)
    text = URL.sub(" ", text)
    cjk = len(CJK.findall(text))
    latin = 0
    for token in CJK.sub(" ", text).split():
        if not identifier_like(token):
            latin += sum(len(w) for w in WORD.findall(token))
    return cjk, latin


def verdict(text):
    """None when the text may pass, else the Chinese reason to give the session."""
    cjk, latin = shares(text)
    if cjk + latin < MIN_LETTERS or cjk / (cjk + latin) >= MIN_SHARE:
        return None
    return (f"上一条回复的正文主要不是中文（中文约占 {cjk * 100 // (cjk + latin)}%，英文字母 {latin} 个）。"
            "按 CLAUDE.md 基线 6，对用户的文字一律用中文：请用中文重写这条回复，内容不变；代码、命令、路径、标识符保持原样。")


def last_assistant_text(payload):
    text = payload.get("last_assistant_message")
    if isinstance(text, str):
        return text
    path = payload.get("transcript_path")
    if not isinstance(path, str):
        return ""
    with open(path, "rb") as f:
        f.seek(0, 2)
        f.seek(max(0, f.tell() - 262144))
        lines = f.read().decode("utf-8", "replace").splitlines()
    for line in reversed(lines):
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if entry.get("type") == "assistant":
            content = entry.get("message", {}).get("content", "")
            if isinstance(content, str):
                return content
            return "\n".join(b.get("text", "") for b in content if isinstance(b, dict) and b.get("type") == "text")
    return ""


def stop(stdin):
    try:
        payload = json.load(stdin)
        if payload.get("stop_hook_active"):  # already asked once this turn: never loop
            return
        reason = verdict(last_assistant_text(payload))
    except Exception:  # fail open: a broken check must not hold up a session
        return
    if reason:
        print(json.dumps({"decision": "block", "reason": reason}, ensure_ascii=False))


def self_test():
    english = "I fixed the install race in the gates directory. The root cause was that a concurrent install moved the installed directory aside, so the other install saw nothing there and gave up. The fix retries for three seconds, and the test now fails on the old code."
    mixed_english_majority = "修复完成。 The root cause was a rename race between two concurrent installs, and the retry loop now waits up to three seconds before it gives up on the directory."
    chinese = ("根因是并发安装时 `scripts/gates/install.sh` 把 <dir>/<sha> 挪开，另一次安装的 ready() 看到目录不存在。修复是在 rename 和 ready 外加最多 3 秒的重试；"
               "见 https://github.com/FORCITIS/forcitis-one-psa/pull/912 和 gates_selftest.py 的 4d。CI 的 check_all.sh 全绿，PR 已入队。")
    code_only = "```bash\n" + "git status --short && echo hello world this is only code\n" * 5 + "```"
    table = "| 项 | 值 |\n|---|---|\n| 命令 | `python3 scripts/gates/gates_selftest.py` |\n| 结果 | 通过，约 107 秒，没有失败的检查项 |\n| 说明 | 4b 用 8 个并发安装，4d 是确定性复现 |"
    chinese_with_english_terms = "这次改动把 retry loop 放在 rename 和 ready 之间：并发 install 同一个 commit 时，race condition 只会让某一次短暂等待，不会再因为目录被别人挪走而失败。"
    reds = {"english report": english, "english majority": mixed_english_majority,
            "english with code block": english + "\n```\nls\n```", "english with chinese heading": "## 结果\n" + english}
    greens = {"chinese with identifiers": chinese, "code only": code_only, "empty": "", "short ack": "Done. PR 912 opened.", "table": table,
              "chinese with english terms": chinese_with_english_terms, "english identifiers in chinese": "已改 scripts/hooks/stop_language.py，见 PR #912，运行 python3 -I x.py --self-test 全绿，用时不到一秒，没有别的改动。"}
    for name, text in reds.items():
        assert verdict(text), ("should block", name, shares(text))
    for name, text in greens.items():
        assert verdict(text) is None, ("should pass", name, shares(text), verdict(text))
    import io
    for payload, blocked in (({"last_assistant_message": english}, True), ({"last_assistant_message": english, "stop_hook_active": True}, False), ({"last_assistant_message": chinese}, False), ({}, False)):
        out = io.StringIO()
        real = sys.stdout
        sys.stdout = out
        try:
            stop(io.StringIO(json.dumps(payload)))
        finally:
            sys.stdout = real
        assert bool(out.getvalue()) == blocked and (not blocked or json.loads(out.getvalue())["decision"] == "block"), (payload.keys(), out.getvalue())
    import os
    import tempfile
    with tempfile.TemporaryDirectory() as d:  # no last_assistant_message: the transcript's last assistant entry is read
        for text, blocked in ((english, True), (chinese, False)):
            path = os.path.join(d, "t.jsonl")
            with open(path, "w", encoding="utf-8") as f:
                f.write(json.dumps({"type": "user", "message": {"content": "x"}}) + "\n" + json.dumps({"type": "assistant", "message": {"content": [{"type": "text", "text": text}]}}) + "\n")
            out = io.StringIO()
            real = sys.stdout
            sys.stdout = out
            try:
                stop(io.StringIO(json.dumps({"transcript_path": path})))
            finally:
                sys.stdout = real
            assert bool(out.getvalue()) == blocked, ("transcript fallback", blocked, out.getvalue())
    out = io.StringIO()
    real = sys.stdout
    sys.stdout = out
    try:
        stop(io.StringIO("not json"))
    finally:
        sys.stdout = real
    assert out.getvalue() == "", "unreadable input fails open"
    long_text = (english + "\n```\n" + "x = 1\n" * 200 + "```\n") * 100  # about 100 KB
    started = time.perf_counter()
    assert verdict(long_text)
    elapsed = time.perf_counter() - started
    assert elapsed < 0.05, f"verdict on {len(long_text)} characters took {elapsed:.3f}s"
    print(f"stop_language self-test: ok ({len(long_text)} characters judged in {elapsed * 1000:.0f} ms)")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else ""
    if mode == "--stop":
        stop(sys.stdin)
    elif mode == "--session-start":
        print(REMINDER)
    elif mode == "--self-test":
        self_test()
    else:
        sys.exit("usage: stop_language.py --stop | --session-start | --self-test")
