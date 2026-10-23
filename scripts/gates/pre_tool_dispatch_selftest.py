#!/usr/bin/env python3
"""pre_tool_dispatch 的自测（临时独立文件，分发本体须 ≤80 行；O8 合并进统一自测）。由 `pre_tool_dispatch.py --self-test` 调起，也可直接运行。
不写任何真实生产样本的字面值（拆段拼接也不行）：真实特征运行时按 target id 从 prod_targets.json 取（prod_write_gate_selftest.real_samples）。"""
import json
import os
import subprocess
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pre_tool_dispatch as d  # noqa: E402
import prod_write_gate as gate  # noqa: E402
from prod_write_gate_selftest import real_samples, seventeen  # noqa: E402

SCRIPT = os.path.join(HERE, "pre_tool_dispatch.py")
PROD_P, PROD_H, PROD_D = real_samples()


def payload(command, **extra):
    return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": extra.pop("cwd", HERE), **extra})


def self_test():
    # 1. the pre-filter never hides a refusal: for every command the dispatcher decides exactly what the gate decides on its own
    corpus = [c for cs in seventeen(PROD_P, PROD_H, PROD_D).values() for c in cs]
    corpus += ["ls", "git status", "echo hi", "cat api.py", "npm test", "", "   ", "python3 x.py", f"git commit -m '{PROD_P} 说明'", f"psql -h {PROD_P} -c 'select 1'", f"aliyun rds DeleteAccount --DBInstanceId {PROD_P}",
               f"aliyun rds DescribeDBInstances --DBInstanceId {PROD_P}", f"python3 -c \"connect('{PROD_P}')\"", f"xargs -I{{}} sqlcmd -S {PROD_P} -Q {{}}", f"sqlcmd -S `echo {PROD_P}` -Q 'delete from t'",
               f"bash -lc \"psql -h {PROD_P} -c 'drop table t'\"", f"$'\\x73qlcmd' -S {PROD_P} -Q 'delete from t'", f"bash <<'EOF'\nsqlcmd -S {PROD_P} -Q 'delete from t'\nEOF", f"env -S 'sqlcmd -S {PROD_P} -Q x'",
               f"curl -XPOST https://{PROD_P}/x", f"al\"\"iyun rds DeleteAccount --DBInstanceId {PROD_P}", "PSQL -c x", "codex exec p", "git push --no-verify", "gh pr create --title x"]
    with tempfile.TemporaryDirectory() as elsewhere:
        for command in corpus:
            raw = payload(command, cwd=elsewhere)
            assert d.dispatch(raw) == gate.decide(json.loads(raw)), command
        # 1b. what the old pre-filter hid (O3 review, M1): interpreters outside its own list, `. file`, a command shlex cannot read; each script names production
        for name in ("deploy", "deploy.lua"):
            with open(os.path.join(elsewhere, name), "w", encoding="utf-8") as f:
                f.write(f"ssh {PROD_H} rm -rf /srv/x\n")
        hidden = ["lua deploy.lua", "dash deploy", "ksh deploy", ". deploy", "nohup dash deploy", "lua deploy.lua && echo ok", "cat sshd_conf 'oops", "sudo -u x ksh deploy", "(. deploy)"]
        for command in hidden:
            raw = payload(command, cwd=elsewhere)
            assert gate.decide(json.loads(raw))[0] == 2 and d.dispatch(raw) == gate.decide(json.loads(raw)), command
    # 2. every one of the 17 cases is refused through the dispatcher, with the gate's own reason
    for number, commands in seventeen(PROD_P, PROD_H, PROD_D).items():
        for command in commands:
            code, out, err = d.dispatch(payload(command))
            assert code == 2 and err.strip(), (number, command)
    # 3. ordinary commands and other tools start nothing; checks that used to hang off the dispatcher are no longer here
    for command in ("ls", "echo hi", "git status", "npm test", "codex exec p", "git push --no-verify", "gh pr create --title x"):
        assert d.dispatch(payload(command)) == (0, "", ""), command
    for raw in (json.dumps({"tool_name": "Agent", "tool_input": {"description": "复审", "prompt": "p"}}), json.dumps({"tool_name": "Read", "tool_input": {"file_path": "x"}}),
                json.dumps({"tool_name": "Bash", "tool_input": {"command": "ls", "description": "跑测试", "run_in_background": True}})):
        assert d.dispatch(raw) == (0, "", ""), raw
    # a Bash call without a string command fails closed (O3 review, suggestion 2; it used to pass)
    for raw in (json.dumps({"tool_name": "Bash", "tool_input": {}}), json.dumps({"tool_name": "Bash", "tool_input": {"command": 1}}), json.dumps({"tool_name": "Bash"}), json.dumps({"tool_input": {"command": None}})):
        code, out, err = d.dispatch(raw)
        assert code == 2 and "按拒绝处理" in err and gate.decide(json.loads(raw))[0] == 2, raw
    # 4. fail closed: unreadable input, a gate that cannot be imported, a gate that crashes
    for raw in ("not json", "[1]", "{"):
        code, out, err = d.dispatch(raw)
        assert code == 2 and "拒绝" in err, raw

    def broken_import():
        raise ImportError("no prod_write_gate")

    class Crashing:
        plain = staticmethod(gate.plain)

        @staticmethod
        def could_concern(text):
            return True

        @staticmethod
        def decide(payload):
            raise RuntimeError("boom")

    class Hanging(Crashing):
        @staticmethod
        def decide(payload):
            time.sleep(5)
            return 0, "", ""

    real_stdin, real_deadline, d.DEADLINE = sys.stdin, d.DEADLINE, 1  # a gate with no answer in time is a failure (O7 final, suggestion 1)
    try:
        for loader in (broken_import, lambda: Crashing, lambda: Hanging):
            sys.stdin = __import__("io").StringIO(payload(f"psql -h {PROD_P} -c 'select 1'"))
            err = __import__("io").StringIO()
            real_stderr, sys.stderr = sys.stderr, err
            try:
                code = d.main(loader)
            finally:
                sys.stderr = real_stderr
            assert code == 2 and "生产写门出错" in err.getvalue() and "按拒绝处理" in err.getvalue(), (loader, code, err.getvalue())
    finally:
        sys.stdin, d.DEADLINE = real_stdin, real_deadline
    # 5. end to end as a hook process under the system python, also with -I
    env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", "")}
    for runner in (["/usr/bin/python3"], ["/usr/bin/python3", "-I"]) if os.path.exists("/usr/bin/python3") else ([sys.executable], [sys.executable, "-I"]):
        refused = subprocess.run([*runner, SCRIPT], input=payload(f"aliyun rds DeleteAccount --DBInstanceId {PROD_P}"), capture_output=True, text=True, env=env)
        assert refused.returncode == 2 and "底线 4" in refused.stderr, refused
        passed = subprocess.run([*runner, SCRIPT], input=payload("ls"), capture_output=True, text=True, env=env)
        assert passed.returncode == 0 and not passed.stdout and not passed.stderr, passed
        bad = subprocess.run([*runner, SCRIPT], input="not json", capture_output=True, text=True, env=env)
        assert bad.returncode == 2 and "拒绝" in bad.stderr, bad
    # 6. size limit from policy.json (gates.max_lines.pre_tool_dispatch)
    limit = json.load(open(os.path.join(gate.REPO_ROOT, "policy.json"), encoding="utf-8"))["gates"]["max_lines"]["pre_tool_dispatch"]
    assert sum(1 for _ in open(SCRIPT, encoding="utf-8")) <= limit, f"pre_tool_dispatch.py 超过 {limit} 行"
    print("pre_tool_dispatch self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test())
