#!/usr/bin/env python3
"""delegate.py 的自测（O20，V16 的自测部分）：桩程序代替 codex 与 pi，不调用任何外部模型、不联网。

在仓库根目录运行（不收目录参数，b2b-plan 0.4）：读当前目录的 scripts/delegate.py 与 policy.json，
在临时目录建一个小仓库与临时 HOME，桩程序经 PATH 注入。核对：两路跑通；禁区路径、不外包类别、卡里密钥被拒；
policy 缺键失败关闭；越界改动机械验证不过；子进程只见白名单环境变量（env -i）、stdin 是 /dev/null、优先级已降、
沙箱与禁网参数在命令行里；禁区文件不进 worktree；台账有记录且台账、输出、diff 不含密钥模式、卡正文与本机私有路径。
用法：delegate_selftest.py（全过退出 0，否则 1）
"""
import json, os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path.cwd()
DELEGATE = ROOT / "scripts" / "delegate.py"
CODEX_MUST = ["-s", "workspace-write", "-c", "sandbox_workspace_write.network_access=false", "--ephemeral"]
PI_MUST = ["--no-session", "--no-extensions", "--no-mcp", "--offline", "--tools"]
PLATFORM_ADDED = {"__CF_USER_TEXT_ENCODING"}  # macOS 在 env -i 之后由系统补上的文本编码变量，不含凭据
CARD_MARKER ="CARD-BODY-MARKER-7d1c"
STUB = r'''#!@PY@
import json, os, sys
from pathlib import Path
here = Path(__file__).resolve().parent
name = Path(sys.argv[0]).name
st, dn = os.fstat(0), os.stat("/dev/null")
rec = {"argv": sys.argv[1:], "env": sorted(os.environ), "stdin_devnull": st.st_rdev == dn.st_rdev and st.st_ino == dn.st_ino,
       "prio": os.getpriority(os.PRIO_PROCESS, 0), "hooks_visible": Path("scripts/hooks/x.py").exists(),
       "secrets_visible": Path("docs/SECRETS-sample.md").exists(), "src_visible": Path("src/a.txt").exists()}
(here / ("rec-" + name + ".json")).write_text(json.dumps(rec))
prompt = sys.argv[-1]
print("workdir: " + os.getcwd(), file=sys.stderr)
Path("src/a.txt").write_text("changed\n")
if "OUT_OF_SCOPE" in prompt:
    Path("scripts/hooks").mkdir(parents=True, exist_ok=True)
    Path("scripts/hooks/x.py").write_text("x = 2\n")
if name == "codex":
    Path(sys.argv[sys.argv.index("-o") + 1]).write_text("改了 src/a.txt\n")
    print("tokens used\n1,234", file=sys.stderr)
else:
    print("改了 src/a.txt")
'''


class T:
    def __init__(self):
        self.fails = 0

    def check(self, cond, what):
        print(("PASS " if cond else "FAIL ") + what)
        self.fails += not cond


def setup(tmp, policy):
    repo, home, stubs = tmp / "repo", tmp / "home", tmp / "stubs"
    for d in (repo, home, stubs):
        d.mkdir()
    for name in ("codex", "pi"):
        (stubs / name).write_text(STUB.replace("@PY@", sys.executable), encoding="utf-8")
        (stubs / name).chmod(0o755)
    env = {"HOME": str(home), "PATH": f"{stubs}:/usr/bin:/bin", "LANG": "C.UTF-8", "GIT_CONFIG_NOSYSTEM": "1",
           "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@example.invalid", "GIT_COMMITTER_NAME": "t",
           "GIT_COMMITTER_EMAIL": "t@example.invalid", "DELEGATE_SELFTEST_CANARY": "1"}
    files = {"policy.json": json.dumps(policy, ensure_ascii=False, indent=2),
             "src/a.txt": "original\n", "scripts/hooks/x.py": "x = 1\n", "docs/SECRETS-sample.md": "placeholder\n",
             "docs/modernization/tasks/T-1.md": f"# T-1\n把 src/a.txt 改成 changed。\n{CARD_MARKER}\n",
             "docs/modernization/tasks/T-2.md": "# T-2\nOUT_OF_SCOPE：顺手改 scripts/hooks/x.py。\n",
             "docs/modernization/tasks/T-3.md": "# T-3\n凭据 " + "sk-" + "Q" * 32 + "\n"}
    for rel, text in files.items():
        (repo / rel).parent.mkdir(parents=True, exist_ok=True)
        (repo / rel).write_text(text, encoding="utf-8")
    for args in (["init", "-q"], ["add", "-A"], ["commit", "-q", "-m", "init"]):
        subprocess.run(["git", *args], cwd=repo, env=env, check=True, stdin=subprocess.DEVNULL)
    return repo, home, stubs, env


def run(repo, env, *args):
    r = subprocess.run([sys.executable, "-I", str(DELEGATE), *args], cwd=repo, env=env, capture_output=True, text=True,
                       stdin=subprocess.DEVNULL, timeout=120)
    return r.returncode, r.stdout + r.stderr


def ledger(home):
    p = home / ".modernize/psa/state/delegate/ledger.jsonl"
    return [json.loads(l) for l in p.read_text(encoding="utf-8").splitlines()] if p.exists() else []


def leak_free(text, t, what):
    secret = "sk-" + "Q" * 32
    t.check(secret not in text and CARD_MARKER not in text and "把 src/a.txt 改成" not in text, f"{what} 不含密钥、卡正文与提示")


def happy(t, engine, stub, must, repo, home, stubs, env, policy):
    rec_file = stubs / f"rec-{stub}.json"
    rec_file.unlink(missing_ok=True)
    rc, out = run(repo, env, "run", "--engine", engine, "--category", "机械改写", "--card", "T-1", "--scope", "src/a.txt")
    t.check(rc == 0, f"{engine} 两路之一跑通（退出 {rc}）" + ("" if rc == 0 else f"：{out.strip()[-300:]}"))
    if not rec_file.exists():
        t.check(False, f"{engine} 桩程序被调用")
        return
    rec = json.loads(rec_file.read_text())
    allow = set(policy["delegate"]["env_allow"])
    extra = set(rec["env"]) - allow - PLATFORM_ADDED
    t.check(not extra and "DELEGATE_SELFTEST_CANARY" not in rec["env"],
            f"{engine} 子进程只见白名单环境变量（env -i）" + (f"；多出 {sorted(extra)}" if extra else ""))
    t.check(rec["stdin_devnull"], f"{engine} 子进程 stdin 是 /dev/null")
    want = min(20, os.getpriority(os.PRIO_PROCESS, 0) + policy["machine"]["delegate_nice"])
    t.check(rec["prio"] >= want, f"{engine} 子进程优先级已降到 {want} 以下（实为 {rec['prio']}）")
    argv = rec["argv"]
    pairs = all(argv[i + 1] == "workspace-write" for i, a in enumerate(argv) if a == "-s") and any(
        a == "-c" and argv[i + 1] == "sandbox_workspace_write.network_access=false" for i, a in enumerate(argv[:-1]))
    t.check(all(m in argv for m in must) and (engine != "codex" or pairs), f"{engine} 沙箱与禁网参数写死在命令行")
    t.check(not rec["hooks_visible"] and not rec["secrets_visible"] and rec["src_visible"], f"{engine} 禁区文件不进 worktree")
    last = ledger(home)[-1]
    t.check(last.get("status") == "ok" and last.get("engine") == engine and last.get("changed_files") == 1, f"{engine} 台账有记录且通过")
    run_dir = home / ".modernize/psa/state/delegate/runs" / last["run_id"]
    diff = (run_dir / "diff.patch").read_text(encoding="utf-8")
    t.check("+changed" in diff and "src/a.txt" in diff, f"{engine} 产出含 diff")
    t.check("改了 src/a.txt" in (run_dir / "out.txt").read_text(encoding="utf-8"), f"{engine} 答复落盘到 out.txt")
    t.check(not (run_dir / "wt").exists(), f"{engine} 运行后 worktree 已移除")
    text = (home / ".modernize/psa/state/delegate/ledger.jsonl").read_text(encoding="utf-8")
    leak_free(text + (run_dir / "out.txt").read_text(encoding="utf-8") + diff, t, f"{engine} 台账、输出、diff")
    t.check(str(home) not in text, f"{engine} 台账不含本机私有路径")
    return last["run_id"]


def refused(t, repo, home, stubs, env, what, *args, expect_ledger=True):
    for s in ("codex", "pi"):
        (stubs / f"rec-{s}.json").unlink(missing_ok=True)
    n = len(ledger(home))
    rc, out = run(repo, env, *args)
    called = (stubs / "rec-codex.json").exists() or (stubs / "rec-pi.json").exists()
    t.check(rc == 2 and not called, f"{what}：拒绝且不起子进程（退出 {rc}）")
    if expect_ledger:
        new = ledger(home)[n:]
        t.check(len(new) == 1 and new[0].get("status") == "rejected", f"{what}：台账记拒绝")
    return out


def main():
    if not DELEGATE.is_file():
        print("须在仓库根目录运行（找不到 scripts/delegate.py）", file=sys.stderr)
        return 1
    policy = json.loads((ROOT / "policy.json").read_text(encoding="utf-8"))
    t = T()
    tmp = Path(tempfile.mkdtemp(prefix="delegate-selftest-")).resolve()
    try:
        repo, home, stubs, env = setup(tmp, policy)
        rid = happy(t, "codex", "codex", CODEX_MUST, repo, home, stubs, env, policy)
        happy(t, "deepseek", "pi", PI_MUST, repo, home, stubs, env, policy)
        base = ["run", "--engine", "codex", "--category", "机械改写", "--card"]
        refused(t, repo, home, stubs, env, "范围是禁区文件", *base, "T-1", "--scope", "scripts/hooks/x.py")
        refused(t, repo, home, stubs, env, "范围覆盖禁区文件", *base, "T-1", "--scope", "scripts")
        refused(t, repo, home, stubs, env, "范围是禁区通配（密钥文件）", *base, "T-1", "--scope", "docs/SECRETS-sample.md")
        refused(t, repo, home, stubs, env, "范围出仓库", *base, "T-1", "--scope", "../x")
        out = refused(t, repo, home, stubs, env, "卡里有密钥模式", *base, "T-3", "--scope", "src/a.txt")
        leak_free(out + json.dumps(ledger(home), ensure_ascii=False), t, "密钥卡被拒后的台账与报错")
        refused(t, repo, home, stubs, env, "类别不在试点", "run", "--engine", "deepseek", "--category", "设计", "--card", "T-1", "--scope", "src/a.txt")
        p2 = json.loads(json.dumps(policy))
        p2["delegate"]["pilot"]["categories"].append("复审")
        (repo / "policy.json").write_text(json.dumps(p2, ensure_ascii=False), encoding="utf-8")
        refused(t, repo, home, stubs, env, "不外包类别（复审）", "run", "--engine", "deepseek", "--category", "复审", "--card", "T-1", "--scope", "src/a.txt")
        for key in ("forbidden_paths", "forbidden_categories", "env_allow", "ledger"):
            p3 = json.loads(json.dumps(policy))
            del p3["delegate"][key]
            (repo / "policy.json").write_text(json.dumps(p3, ensure_ascii=False), encoding="utf-8")
            refused(t, repo, home, stubs, env, f"policy 缺 delegate.{key} 失败关闭", *base, "T-1", "--scope", "src/a.txt", expect_ledger=False)
        p4 = json.loads(json.dumps(policy))
        p4["machine"]["delegate_nice"] = None
        (repo / "policy.json").write_text(json.dumps(p4, ensure_ascii=False), encoding="utf-8")
        refused(t, repo, home, stubs, env, "machine.delegate_nice 为 null 失败关闭", *base, "T-1", "--scope", "src/a.txt", expect_ledger=False)
        (repo / "policy.json").write_text(json.dumps(policy, ensure_ascii=False), encoding="utf-8")
        rc, _ = run(repo, env, *base, "T-2", "--scope", "src/a.txt")
        last = ledger(home)[-1]
        t.check(rc == 1 and last.get("status") == "verify_failed" and "touch_forbidden" in last.get("verify", [])
                and "out_of_scope" in last.get("verify", []), "越界改禁区：机械验证不过、台账记 verify_failed")
        rc, _ = run(repo, env, "adopt", rid or "x", "--adopted", "yes", "--rework", "1")
        t.check(rc == 0 and ledger(home)[-1].get("event") == "adopt", "adopt 记采纳与返工轮次")
        rc, out = run(repo, env, "summary")
        t.check(rc == 0 and "codex" in out and "deepseek" in out, "summary 按月按引擎汇总")
        wts = subprocess.run(["git", "worktree", "list"], cwd=repo, env=env, capture_output=True, text=True).stdout
        t.check(len(wts.strip().splitlines()) == 1, "没有残留 worktree")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"delegate 自测：{'全过' if not t.fails else f'{t.fails} 项失败'}")
    return 1 if t.fails else 0


if __name__ == "__main__":
    sys.exit(main())
