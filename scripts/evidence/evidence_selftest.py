#!/usr/bin/env python3
"""取证单入口 evidence.py 的自测（二乙 #6 O22a）：登记行由入口生成且与 prod_targets.json 那一条相等；输出文件名与文件头带过期日期、0700 目录 0600 文件；
空环境、标准输入为 /dev/null（无口令）即可跑；要口令的取证单失败；启动方式、参数、policy 缺项、输出目录为符号链接都拒。门的判定（登记写法放行、其余不可信）在 gates_selftest.py 第 9 段。
全部用副本在临时目录里跑：副本只把取家目录那一处换成临时家目录，取证单是桩，不连生产。用法：evidence_selftest.py（light.sh 带 --self-test 调用，参数忽略）
"""
import datetime
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
PYTHON = "/usr/bin/python3" if os.path.exists("/usr/bin/python3") else sys.executable
HOME_LINE = "return pwd.getpwuid(os.getuid()).pw_dir"


def main():
    out = subprocess.run([PYTHON, "-I", "-S", os.path.join(HERE, "evidence.py"), "--entry"], capture_output=True, text=True, env={})
    table = json.loads(open(os.path.join(ROOT, "scripts", "gates", "prod_targets.json"), encoding="utf-8").read())["trusted_entries"]["entries"]
    assert out.returncode == 0 and [e for e in table if e.get("id") == "evidence"] == [json.loads(out.stdout)], ("the table's row is the one the entry generates", out.stdout, out.stderr)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        repo, home = os.path.join(tmp, "repo"), os.path.join(tmp, "home")
        here = os.path.join(repo, "scripts", "evidence")
        os.makedirs(here)
        os.makedirs(home)
        source = open(os.path.join(HERE, "evidence.py"), encoding="utf-8").read()
        assert source.count(HOME_LINE) == 1, "the copy swaps exactly the one place the home is read"
        open(os.path.join(here, "evidence.py"), "w", encoding="utf-8").write(source.replace(HOME_LINE, f"return {home!r}"))
        sheets = {"stub": "def collect(session):\n    return ['a ' + session, 'b']\n", "ask": "import getpass\ndef collect(session):\n    return [getpass.getpass('口令：')]\n",
                  "boom": "def collect(session):\n    raise ValueError('detail-from-the-sheet')\n", "feed": "import sys\ndef collect(session):\n    return ['in:' + sys.stdin.read()]\n"}
        for name, body in sheets.items():
            open(os.path.join(here, f"sheet_{name}.py"), "w", encoding="utf-8").write(body)
        os.symlink(os.path.join(here, "sheet_stub.py"), os.path.join(here, "sheet_link.py"))

        def policy(value):
            open(os.path.join(repo, "policy.json"), "w", encoding="utf-8").write(json.dumps({"retention_days": {"evidence": value}} if value is not None else {}))

        def entry(*args, flags=("-I", "-S"), feed=None):  # an empty environment; standard input /dev/null, or a pipe fed `feed`
            return subprocess.run([PYTHON, *flags, os.path.join(here, "evidence.py"), *args], capture_output=True, text=True, env={}, **({"input": feed} if feed else {"stdin": subprocess.DEVNULL}))

        def written():
            where = os.path.join(home, ".modernize", "psa", "ops", "evidence")
            return sorted(os.listdir(where)) if os.path.isdir(where) else []

        policy(7)
        r = entry("--session=one-ops", "stub")
        expires = datetime.datetime.now(datetime.timezone.utc).date() + datetime.timedelta(days=7)
        assert r.returncode == 0 and len(written()) == 1, (r.returncode, r.stderr)
        path = r.stdout.strip()
        assert path == os.path.join(home, ".modernize", "psa", "ops", "evidence", written()[0]) and "a one-ops" not in r.stdout, "only the path is printed, not the content"
        assert written()[0].startswith("stub-one-ops-") and written()[0].endswith(f"-expires-{expires:%Y%m%d}.txt"), ("the name carries the expiry janitor reads", written())
        lines = open(path, encoding="utf-8").read().splitlines()
        assert lines[0].startswith(f"过期：{expires:%Y-%m-%d}") and lines[-2:] == ["a one-ops", "b"], lines
        assert os.stat(path).st_mode & 0o777 == 0o600 and os.stat(os.path.dirname(path)).st_mode & 0o777 == 0o700, "0600 file in a 0700 directory"
        for args, flags, why in ((("--session=one-ops", "stub"), ("-I",), "须由 /usr/bin/python3 -I -S"), (("--session=one-ops", "stub"), (), "须由 /usr/bin/python3 -I -S"), (("--session=one-ops", "ask"), ("-I", "-S"), "不得要口令"),
                                 (("--session=one-ops", "boom"), ("-I", "-S"), "取证单 boom 出错"), (("--session=one-ops", "none"), ("-I", "-S"), "没有取证单"),
                                 (("--session=One", "stub"), ("-I", "-S"), "会话名只许"), (("--session=one-ops", "../stub"), ("-I", "-S"), "会话名只许"),
                                 (("--session=one-ops",), ("-I", "-S"), "用法"), (("stub", "--session=one-ops"), ("-I", "-S"), "用法"), (("--session=one-ops", "link"), ("-I", "-S"), "没有取证单")):
            r = entry(*args, flags=flags)
            assert r.returncode == 2 and why in r.stderr and len(written()) == 1, ("refused for its own reason, nothing written", why, r.returncode, r.stderr)
        assert "detail-from-the-sheet" not in entry("--session=one-ops", "boom").stderr, "a failing sheet: its exception type only, not its text"
        for value, why in ((None, "missing key"), ("7", "a string"), (0, "zero"), (True, "a boolean")):
            policy(value)
            r = entry("--session=one-ops", "stub")
            assert r.returncode == 2 and "retention_days.evidence" in r.stderr and len(written()) == 1, (why, r.stderr)
        policy(7)
        victim, start = os.path.join(tmp, "victim"), datetime.datetime.now(datetime.timezone.utc)
        open(victim, "w", encoding="utf-8").write("kept\n")
        names = [os.path.join(os.path.dirname(path), f"stub-one-ops-{t:%Y%m%dT%H%M%SZ}-expires-{(t + datetime.timedelta(days=7)).date():%Y%m%d}.txt") for t in (start + datetime.timedelta(seconds=s) for s in range(8))]
        for plant in (lambda p: os.symlink(victim, p), lambda p: shutil.copyfile(victim, p)):  # at the output name of each of the next seconds a link, then a file: never followed, never overwritten (O_NOFOLLOW, O_EXCL)
            for planted in names:
                os.path.lexists(planted) and os.path.islink(planted) and os.remove(planted)
                os.path.lexists(planted) or plant(planted)
            r = entry("--session=one-ops", "stub")
            assert r.returncode == 2 and "写不了输出文件" in r.stderr and all(open(p, encoding="utf-8").read() == "kept\n" for p in names + [victim] if not os.path.islink(p) and p != path), (r.returncode, r.stderr)
        r = entry("--session=one-ops", "feed", feed="secret-typed-in\n")
        assert r.returncode == 0 and open(r.stdout.strip(), encoding="utf-8").read().splitlines()[-1] == "in:", "a sheet reads /dev/null, not what was piped to the entry"
        shutil.rmtree(os.path.join(home, ".modernize", "psa", "ops"))
        os.makedirs(os.path.join(tmp, "elsewhere"))
        os.symlink(os.path.join(tmp, "elsewhere"), os.path.join(home, ".modernize", "psa", "ops"))
        r = entry("--session=one-ops", "stub")
        assert r.returncode == 2 and "符号链接" in r.stderr and os.listdir(os.path.join(tmp, "elsewhere")) == [], "an output directory reached through a symlink is refused"
        os.remove(os.path.join(home, ".modernize", "psa", "ops"))
        os.rename(os.path.join(home, ".modernize", "psa"), os.path.join(home, ".modernize", "psa-real"))
        os.symlink(os.path.join(home, ".modernize", "psa-real"), os.path.join(home, ".modernize", "psa"))
        r = entry("--session=one-ops", "stub")
        assert r.returncode == 2 and "符号链接" in r.stderr and not os.path.exists(os.path.join(home, ".modernize", "psa-real", "ops")), "~/.modernize/psa itself a symlink: refused"
    print("evidence entry: ok")


if __name__ == "__main__":
    main()
