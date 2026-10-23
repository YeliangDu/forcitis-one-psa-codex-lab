#!/usr/bin/env python3
"""pre_tool_dispatch 的自测（临时独立文件，分发本体须 ≤80 行；O8 合并进统一自测）。由 `pre_tool_dispatch.py --self-test` 调起，也可直接运行。
不写真实生产样本的字面值（拼接而成），否则门会拦自己的自测。"""
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import pre_tool_dispatch as d  # noqa: E402
import prod_write_gate as gate  # noqa: E402
from prod_write_gate_selftest import IP, PRODDB, RDS, seventeen  # noqa: E402

SCRIPT = os.path.join(HERE, "pre_tool_dispatch.py")


def payload(command, **extra):
    return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": extra.pop("cwd", HERE), **extra})


def self_test():
    # 1. the pre-filter never hides a refusal: for every command the dispatcher decides exactly what the gate decides on its own
    corpus = [c for cs in seventeen(RDS, IP, PRODDB).values() for c in cs]
    corpus += ["ls", "git status", "echo hi", "cat api.py", "npm test", "", "   ", "python3 x.py", f"git commit -m '{RDS} 说明'", f"psql -h {RDS} -c 'select 1'", f"aliyun rds DeleteAccount --DBInstanceId {RDS}",
               f"aliyun rds DescribeDBInstances --DBInstanceId {RDS}", f"python3 -c \"connect('{RDS}')\"", f"xargs -I{{}} sqlcmd -S {RDS} -Q {{}}", f"sqlcmd -S `echo {RDS}` -Q 'delete from t'",
               f"bash -lc \"psql -h {RDS} -c 'drop table t'\"", f"$'\\x73qlcmd' -S {RDS} -Q 'delete from t'", f"bash <<'EOF'\nsqlcmd -S {RDS} -Q 'delete from t'\nEOF", f"env -S 'sqlcmd -S {RDS} -Q x'",
               f"curl -XPOST https://{RDS}/x", f"al\"\"iyun rds DeleteAccount --DBInstanceId {RDS}", "PSQL -c x", "codex exec p", "git push --no-verify", "gh pr create --title x"]
    with tempfile.TemporaryDirectory() as elsewhere:
        for command in corpus:
            raw = payload(command, cwd=elsewhere)
            assert d.dispatch(raw) == gate.decide(json.loads(raw)), command
    # 2. every one of the 17 cases is refused through the dispatcher, with the gate's own reason
    for number, commands in seventeen(RDS, IP, PRODDB).items():
        for command in commands:
            code, out, err = d.dispatch(payload(command))
            assert code == 2 and err.strip(), (number, command)
    # 3. ordinary commands and other tools start nothing; checks that used to hang off the dispatcher are no longer here
    for command in ("ls", "echo hi", "git status", "npm test", "codex exec p", "git push --no-verify", "gh pr create --title x"):
        assert d.dispatch(payload(command)) == (0, "", ""), command
    for raw in (json.dumps({"tool_name": "Agent", "tool_input": {"description": "复审", "prompt": "p"}}), json.dumps({"tool_name": "Read", "tool_input": {"file_path": "x"}}),
                json.dumps({"tool_name": "Bash", "tool_input": {"command": "ls", "description": "跑测试", "run_in_background": True}}), json.dumps({"tool_name": "Bash", "tool_input": {}})):
        assert d.dispatch(raw) == (0, "", ""), raw
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

    real_stdin = sys.stdin
    try:
        for loader in (broken_import, lambda: Crashing):
            sys.stdin = __import__("io").StringIO(payload(f"psql -h {RDS} -c 'select 1'"))
            err = __import__("io").StringIO()
            real_stderr, sys.stderr = sys.stderr, err
            try:
                code = d.main(loader)
            finally:
                sys.stderr = real_stderr
            assert code == 2 and "生产写门出错" in err.getvalue() and "按拒绝处理" in err.getvalue(), (loader, code, err.getvalue())
    finally:
        sys.stdin = real_stdin
    # 5. end to end as a hook process under the system python, also with -I
    env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", "")}
    for runner in (["/usr/bin/python3"], ["/usr/bin/python3", "-I"]) if os.path.exists("/usr/bin/python3") else ([sys.executable], [sys.executable, "-I"]):
        refused = subprocess.run([*runner, SCRIPT], input=payload(f"aliyun rds DeleteAccount --DBInstanceId {RDS}"), capture_output=True, text=True, env=env)
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
