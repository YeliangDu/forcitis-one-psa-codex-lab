#!/usr/bin/env python3
from datetime import datetime, timedelta
import json
import os
from pathlib import Path
import secrets
import shutil
import stat
import subprocess
import sys
import tempfile
import time


HOOKS = Path(__file__).resolve().parent


def invoke(script, payload, env, cwd, timeout=22):
    result = subprocess.run(["/bin/bash", str(HOOKS / script)], input=payload if isinstance(payload, str) else json.dumps(payload),
                            cwd=cwd, env=env, capture_output=True, text=True, encoding="utf-8", timeout=timeout)
    assert result.returncode == 0, f"{script} must exit 0"
    assert not result.stderr, f"{script} must not expose stderr"
    return result.stdout


def self_test():
    with tempfile.TemporaryDirectory(prefix="handoff-") as tmp:
        base = Path(tmp).resolve()
        home, repo, bin_dir = (base / p for p in ["home", "worktree", "bin"])
        for path in [home, repo, bin_dir]:
            path.mkdir()
        git = shutil.which("git")
        assert git, "self-test requires local git"
        for name, executable in [("git", git), ("python3", sys.executable), ("dirname", "/usr/bin/dirname")]:
            (bin_dir / name).symlink_to(executable)
        env = {"HOME": str(home), "LC_ALL": "C", "PATH": str(bin_dir), "GIT_CONFIG_NOSYSTEM": "1",
               "GIT_CONFIG_GLOBAL": os.devnull, "GIT_AUTHOR_NAME": "handoff", "GIT_COMMITTER_NAME": "handoff",
               "GIT_AUTHOR_EMAIL": "handoff@example.invalid", "GIT_COMMITTER_EMAIL": "handoff@example.invalid"}

        def local_git(*args):
            return subprocess.run([git, "-C", str(repo), *args], env=env, check=True, capture_output=True, text=True).stdout.strip()

        local_git("init", "-b", "claude/one-test")
        local_git("-c", "core.hooksPath=/dev/null", "commit", "--allow-empty", "-m", "handoff baseline")
        for i in range(60):
            (repo / f"change-{i:02}.txt").write_text("changed\n", encoding="utf-8")
        hooks = repo / "scripts/hooks"
        hooks.mkdir(parents=True)
        for script in ["session_context.py", "start_sheet.py"]:
            shutil.copy2(HOOKS / script, hooks / script)
        playbook = repo / "docs/modernization/SESSION_PLAYBOOK.md"
        playbook.parent.mkdir(parents=True)
        playbook.write_text("## 11. 调度\n1. **硬规则**：正文不应注入。\n## 12. 其他\n", encoding="utf-8")
        profile = playbook.parent / "sessions/one-test.md"
        profile.parent.mkdir()
        profile.write_text("# one-test\nPROFILE-MARKER\n", encoding="utf-8")
        transcript = base / "transcript.jsonl"
        secret = secrets.token_urlsafe(32)
        records = [{"message": {"content": f'<cross-session-message from-name="one-peer">报告：标题-{i}\n正文不应保存</cross-session-message>'}} for i in range(12)]
        records.append({"message": {"content": [{"type": "text", "text": f'<cross-session-message from-name="one-private">token={secret}\n正文不应保存</cross-session-message>'}]}})
        transcript.write_text("\n".join(json.dumps(r) for r in records) + "\n", encoding="utf-8")
        payload = {"session_id": "old-session", "cwd": str(repo), "transcript_path": str(transcript), "reason": "clear"}
        directory = home / ".modernize/psa/handoff"

        invoke("sessionend_snapshot.sh", {**payload, "reason": "logout"}, env, repo)
        assert not directory.exists(), "non-clear must not create snapshot directory"
        assert invoke("sessionend_snapshot.sh", payload, env, repo) == ""
        snapshots = list(directory.glob("*-old-session-clear.md"))
        assert len(snapshots) == 1, "clear must create one snapshot"
        snapshot = snapshots[0]
        text = snapshot.read_text(encoding="utf-8")
        assert stat.S_IMODE(directory.stat().st_mode) == 0o700
        assert stat.S_IMODE(snapshot.stat().st_mode) == 0o600
        assert f"- 工作目录：{repo}" in text.splitlines()
        assert "claude/one-test" in text and str(transcript) in text and "handoff baseline" in text
        changes = text.split("## 未提交改动\n")[1].split("\n## ")[0].strip().splitlines()
        assert len(changes) == 50, "status output must be capped at 50"
        assert "命令未安装或不可用" in text, "missing gh must be recorded"
        titles = text.split("## 最近跨会话消息标题\n")[1]
        assert titles.count("  · one-peer：") == 9 and "标题-2\n" not in titles and "标题-3\n" in titles
        assert secret not in text and "敏感内容已隐藏" in text and "正文不应保存" not in text

        new_session = {**payload, "session_id": "new-session"}
        restored = invoke("postcompact_context.sh", new_session, env, repo)
        assert "最新交接快照" in restored and "old-session-clear.md" in restored, "same worktree must survive changed session id"
        assert "【开工单】会话 one-test" in restored and "正文不应注入" not in restored, "start injects the start sheet, not rule text"
        assert "标题-" not in restored and "handoff baseline" not in restored, "the snapshot is named by path, never inlined"
        assert secret not in restored

        snapshot.unlink()
        restored = invoke("postcompact_context.sh", new_session, env, repo)
        assert "最新交接快照" not in restored and "【开工单】会话 one-test" in restored and "无在途任务，向调度要卡" in restored, "no snapshot must still give the sheet"

        long_text = "很长" * 3000
        profile.write_text("# one-test\n\n## 类型\n\n工作\n\n" + "".join(f"## {name}\n\n{long_text}\n\n" for name in ["职责", "独占", "不碰", "输入", "输出", "权限边界"]), encoding="utf-8")
        restored = invoke("postcompact_context.sh", new_session, env, repo)
        assert len(restored.partition("\n\n最新交接快照")[0].encode()) <= 8192 and "【职责】" in restored and "全文见原文" in restored, "a huge profile is cut to the sheet limit with a mark"
        profile.write_text("# one-test\nPROFILE-MARKER\n", encoding="utf-8")
        sheet_script = hooks / "start_sheet.py"
        sheet_backup = sheet_script.read_text(encoding="utf-8")
        sheet_script.write_text("import sys\nsys.exit(3)\n", encoding="utf-8")
        broken = invoke("postcompact_context.sh", new_session, env, repo)
        assert "【开工单】告警：start_sheet.py 不可用" in broken, "a missing sheet is announced"
        sheet_script.write_text(sheet_backup, encoding="utf-8")

        def fixture(age, session, body, root=repo, clear=False):
            stamp = datetime.now() - timedelta(days=age)
            path = directory / f"{stamp:%Y%m%d-%H%M%S}-{session}{'-clear' if clear else ''}.md"
            path.write_text((f"- 工作目录：{root}\n" if root else "") + body, encoding="utf-8")
            return path

        old = fixture(8, "previous", "STALE-MARKER")
        restored = invoke("postcompact_context.sh", new_session, env, repo)
        assert "已超过 7 天" in restored and "previous.md" in restored
        old.unlink()
        precompact = fixture(2, "compact", f"COMPACT-MARKER\npassword={secret}")
        newer = fixture(1, "clear", "NEWEST-MARKER", clear=True)
        unrelated = fixture(0, "unrelated", "UNRELATED-MARKER", root=base / "other")
        os.utime(precompact, (time.time() + 3600, time.time() + 3600))
        restored = invoke("postcompact_context.sh", new_session, env, repo)
        assert "-clear.md" in restored and "-compact.md" not in restored and "-unrelated.md" not in restored, "the newest snapshot of this worktree is named"
        part_c_text = restored.split("## C 交接")[1].split("## D")[0]
        assert "-clear.md" in part_c_text and "也没有交接快照" not in part_c_text, "the snapshot reaches the sheet: part C names it instead of claiming there is none"
        newer.unlink()
        restored = invoke("postcompact_context.sh", new_session, env, repo)
        assert "-compact.md" in restored, "precompact snapshots must also be claimed"
        assert secret not in restored
        precompact.unlink()
        unrelated.unlink()
        legacy = fixture(1, "new-session", "LEGACY-MARKER", root=None)
        assert "-new-session.md" in invoke("postcompact_context.sh", new_session, env, repo), "session-id fallback must remain"
        legacy.unlink()

        compact_output = invoke("precompact_snapshot.sh", {**payload, "trigger": "auto"}, env, repo)
        assert "压缩前快照已写入" in compact_output, "precompact wrapper must publish a snapshot"
        compact_file = max(directory.glob("*-old-session.md"))
        assert "# 压缩前快照（auto）" in compact_file.read_text(encoding="utf-8")
        assert compact_file.name in invoke("postcompact_context.sh", new_session, env, repo)
        compact_file.unlink()

        gh = bin_dir / "gh"
        gh.write_text('#!/bin/bash\nprintf "%s\\n" "$*" >> "$HOME/gh-calls"\nprintf "%s\\n" "#7 [OPEN] local-pr"\n', encoding="utf-8")
        gh.chmod(0o700)
        alias_payload = {k: v for k, v in payload.items() if k != "reason"}
        invoke("sessionend_snapshot.sh", {**alias_payload, "session_end_reason": "clear"}, env, repo)
        calls = (home / "gh-calls").read_text(encoding="utf-8").splitlines()
        assert len(calls) == 2 and all("pr list" in c and "--state open" in c for c in calls)
        assert "--head claude/one-test" in calls[0] and "--author @me" in calls[1]
        text = max(directory.glob("*.md")).read_text(encoding="utf-8")
        assert text.count("#7 [OPEN] local-pr") == 2
        assert "gh 账号下的在开 PR（含他人会话的）" in text, "PR ownership must refer to the gh account"

        gh.write_text(f'#!/bin/bash\nprintf "%s\\n" "{secret}" >&2\nexit 1\n', encoding="utf-8")
        invoke("sessionend_snapshot.sh", payload, env, repo)
        text = max(directory.glob("*.md")).read_text(encoding="utf-8")
        assert "命令执行失败" in text and secret not in text
        gh.write_text('#!/bin/bash\nexec python3 -I -c "import time; time.sleep(30)"\n', encoding="utf-8")
        before = time.monotonic()
        invoke("sessionend_snapshot.sh", payload, env, repo, timeout=15)
        assert time.monotonic() - before < 13, "both gh calls share a 10-second budget"
        assert "执行超时" in max(directory.glob("*.md")).read_text(encoding="utf-8")
        gh.unlink()

        for script in ["sessionend_snapshot.sh", "postcompact_context.sh", "precompact_snapshot.sh"]:
            for invalid in ["{", "[]", "null"]:
                assert "告警" in invoke(script, invalid, env, repo)
            assert "告警" in invoke(script, payload, {k: v for k, v in env.items() if k != "HOME"}, repo)
            assert "告警" in invoke(script, payload, {**env, "PATH": "/missing"}, repo)
        invoke("sessionend_snapshot.sh", {**payload, "cwd": "/missing", "session_id": "../invalid"}, env, repo)
        invalid = max(directory.glob("*.md")).read_text(encoding="utf-8")
        assert "unknown" in invalid and "无法读取工作目录" in invalid
        invoke("sessionend_snapshot.sh", {**payload, "transcript_path": str(base / "missing")}, env, repo)
        assert "无（读取失败：FileNotFoundError）" in max(directory.glob("*.md")).read_text(encoding="utf-8")
        blocked = base / "blocked-home"
        blocked.write_text("file", encoding="utf-8")
        assert "告警" in invoke("sessionend_snapshot.sh", payload, {**env, "HOME": str(blocked)}, repo)
    for script in ["handoff_safe_selftest.py", "handoff_storage_selftest.py"]:
        result = subprocess.run([sys.executable, "-W", "error", "-I", str(HOOKS / script), "--self-test"], capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        print(f"{script}: ok")
    print("self-test: ok (reasons, worktree/fallback/age, profile, permissions, redaction, messages, gh offline/timeout, minimal environment/errors)")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        sys.exit("usage: handoff_selftest.py --self-test")
