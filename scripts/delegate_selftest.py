#!/usr/bin/env python3
"""delegate.py 的自测（O20，V16 的自测部分）：桩程序代替 codex 与 pi，不调用任何外部模型、不联网。

在仓库根目录运行（不收目录参数，b2b-plan 0.4）：读当前目录的 scripts/delegate.py 与 policy.json，
在临时目录建一个小仓库与临时 HOME，桩程序经 PATH 注入；卡从 Issue 读（#49），gh 也是桩，经环境变量 DELEGATE_GH 注入，Issue 内容在临时目录的 issues.json。
核对：卡只取「目标」「完成标准」两节；Issue 取不到、不存在、已关闭、缺本会话标签、缺节、卡号不是 Issue 号一律拒且拒绝不带正文；gh 只被以 issue view 只读调用；
两路跑通；real_run_enabled 为 false 时无桩标记的程序被拒；
禁区路径（含 review.final_paths、./ 前缀、大小写变体）、不外包类别、卡里密钥被拒；policy 缺键或类型不对失败关闭；
越界、改名绕范围的改动机械验证不过；子进程只见白名单环境变量（env -i）、stdin 是 /dev/null（自测给的是管道）、
优先级已降、沙箱与禁网参数在命令行里；快照在系统临时目录、不含禁区文件与内容、经 git 拿不到仓库；
台账有记录，token、费用未知记「未知」且汇总不按 0 计；台账、输出、diff 不含密钥模式、卡正文与本机私有路径。
用法：delegate_selftest.py（全过退出 0，否则 1）
"""
import json, os, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path.cwd()
DELEGATE = ROOT / "scripts" / "delegate.py"
CODEX_MUST = ["-s", "workspace-write", "-c", "sandbox_workspace_write.network_access=false", 'approval_policy="never"', "--ephemeral"]
PI_MUST = ["--no-session", "--no-extensions", "--no-mcp", "--offline", "--tools"]
PLATFORM_ADDED = {"__CF_USER_TEXT_ENCODING"}  # macOS 在 env -i 之后由系统补上的文本编码变量，不含凭据
CARD_MARKER = "CARD-BODY-MARKER-7d1c"
FORBID = "FORBIDDEN-CONTENT-5e2a"
MARK_LINE = "# DELEGATE-SELFTEST-STUB\n"
OTHER = "OTHER-SECTION-MARKER-3b9f"   # 两节之外的正文与模板注释，不得进提示
BODY = "REFUSED-BODY-MARKER-9c4e"     # 被拒 Issue 的正文，不得出现在报错与台账
SESSION = "one-test"
LABEL = f"会话:{SESSION}"


def card(goal, done="src/a.txt 内容为 changed。", extra=""):
    return f"<!-- {OTHER} -->\n## 目标\n{goal}\n\n## 完成标准\n{done}\n{extra}"


# 原 docs/modernization/tasks/T-1..T-4.md 四份卡样本改为 Issue #1..#4（意图不变）；#5..#9 是新加的准入拒绝样本。
ISSUES = {"1": {"state": "OPEN", "labels": [{"name": LABEL}], "body": card(f"把 src/a.txt 改成 changed。\n{CARD_MARKER}", extra=f"\n## 备注\n{OTHER}\n")},
          "2": {"state": "OPEN", "labels": [{"name": LABEL}], "body": card("OUT_OF_SCOPE：顺手改 scripts/hooks/x.py。")},
          "3": {"state": "OPEN", "labels": [{"name": LABEL}], "body": card("凭据 " + "sk-" + "Q" * 32)},
          "4": {"state": "OPEN", "labels": [{"name": LABEL}], "body": card("RENAME_IN：把 docs/other.md 搬进 src。")},
          "5": {"state": "CLOSED", "labels": [{"name": LABEL}], "body": card(f"把 src/a.txt 改成 changed。{BODY}")},
          "6": {"state": "OPEN", "labels": [{"name": "会话:one-other"}, {"name": "挂起"}], "body": card(f"把 src/a.txt 改成 changed。{BODY}")},
          "7": {"state": "OPEN", "labels": [{"name": LABEL}], "body": f"## 目标\n把 src/a.txt 改成 changed。{BODY}\n\n## 完成标准\n<!-- 未填 -->\n"},
          "8": f"not json {BODY}", "9": [{"state": "OPEN"}]}
GH_STUB = r'''#!@PY@
import json, os, sys
d, a = os.environ["DELEGATE_SELFTEST_GH_DIR"], sys.argv[1:]
with open(os.path.join(d, "calls.jsonl"), "a") as f:
    f.write(json.dumps(a) + "\n")
issues = json.load(open(os.path.join(d, "issues.json"), encoding="utf-8"))
if a[:2] != ["issue", "view"] or len(a) < 3 or a[2] not in issues:
    sys.exit(print("GraphQL: Could not resolve to an issue or pull request", file=sys.stderr) or 1)
v = issues[a[2]]
print(v if isinstance(v, str) else json.dumps(v, ensure_ascii=False))
'''
STUB = r'''#!@PY@
# DELEGATE-SELFTEST-STUB
import json, os, subprocess, sys
from pathlib import Path
here = Path(__file__).resolve().parent
name = Path(sys.argv[0]).name
st, dn = os.fstat(0), os.stat("/dev/null")
seen = [p for r, _, fs in os.walk(".") for p in (os.path.join(r, f) for f in fs) if "SECRETS" in p or "hooks" in p or "merge" in p
        or "FORBIDDEN-CONTENT-5e2a" in open(p, "rb").read().decode("utf-8", "replace")]
g = subprocess.run(["git", "ls-files", "-s"], capture_output=True, text=True, stdin=subprocess.DEVNULL)
rec = {"argv": sys.argv[1:], "env": sorted(os.environ), "stdin_devnull": st.st_rdev == dn.st_rdev and st.st_ino == dn.st_ino,
       "prio": os.getpriority(os.PRIO_PROCESS, 0), "seen": seen, "src_visible": Path("src/a.txt").exists(),
       "git_rc": g.returncode, "cwd": os.getcwd(), "parent_mode": os.stat("..").st_mode & 0o777}
(here / ("rec-" + name + ".json")).write_text(json.dumps(rec))
prompt = sys.argv[-1]
print("workdir: " + os.getcwd(), file=sys.stderr)
Path("src/a.txt").write_text("changed\n")
if "OUT_OF_SCOPE" in prompt:
    Path("scripts/hooks").mkdir(parents=True, exist_ok=True)
    Path("scripts/hooks/x.py").write_text("x = 2\n")
if "RENAME_IN" in prompt:
    os.rename("docs/other.md", "src/b.md")
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
    repo, home, stubs, plain, ghd = tmp / "repo", tmp / "home", tmp / "stubs", tmp / "plain", tmp / "gh"
    for d in (repo, home, stubs, plain, ghd):
        d.mkdir()
    (ghd / "gh").write_text(GH_STUB.replace("@PY@", sys.executable), encoding="utf-8")
    (ghd / "gh").chmod(0o755)
    (ghd / "issues.json").write_text(json.dumps(ISSUES, ensure_ascii=False), encoding="utf-8")
    for name in ("codex", "pi"):
        for d, text in ((stubs, STUB), (plain, STUB.replace(MARK_LINE, ""))):
            (d / name).write_text(text.replace("@PY@", sys.executable), encoding="utf-8")
            (d / name).chmod(0o755)
    env = {"HOME": str(home), "PATH": f"{stubs}:/usr/bin:/bin", "LANG": "C.UTF-8", "GIT_CONFIG_NOSYSTEM": "1",
           "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@example.invalid", "GIT_COMMITTER_NAME": "t",
           "GIT_COMMITTER_EMAIL": "t@example.invalid", "DELEGATE_SELFTEST_CANARY": "1",
           "DELEGATE_GH": str(ghd / "gh"), "DELEGATE_SELFTEST_GH_DIR": str(ghd)}
    files = {"policy.json": json.dumps(policy, ensure_ascii=False, indent=2), "src/a.txt": "original\n", "docs/other.md": "other\n",
             "scripts/hooks/x.py": f"x = 1  # {FORBID}\n", "docs/SECRETS-sample.md": f"{FORBID}\n", "scripts/merge/m.py": f"# {FORBID}\n"}
    for rel, text in files.items():
        (repo / rel).parent.mkdir(parents=True, exist_ok=True)
        (repo / rel).write_text(text, encoding="utf-8")
    for args in (["init", "-q"], ["add", "-A"], ["commit", "-q", "-m", "init"]):
        subprocess.run(["git", *args], cwd=repo, env=env, check=True, stdin=subprocess.DEVNULL)
    return repo, home, stubs, plain, env


def run(repo, env, *args):
    """stdin 给一个管道（不是 /dev/null），子进程看到 /dev/null 只能是 delegate 自己重定向的。"""
    r = subprocess.run([sys.executable, "-I", str(DELEGATE), *args], cwd=repo, env=env, capture_output=True, text=True,
                       stdin=subprocess.PIPE, timeout=120)
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
    rc, out = run(repo, env, "run", "--engine", engine, "--category", "机械改写", "--session", SESSION, "--card", "1", "--scope", "src/a.txt")
    t.check(rc == 0, f"{engine} 两路之一跑通（退出 {rc}）" + ("" if rc == 0 else f"：{out.strip()[-300:]}"))
    if not rec_file.exists():
        t.check(False, f"{engine} 桩程序被调用")
        return
    rec = json.loads(rec_file.read_text())
    prompt = rec["argv"][-1]
    t.check(CARD_MARKER in prompt and "src/a.txt 内容为 changed" in prompt and OTHER not in prompt,
            f"{engine} 提示里的卡取自 Issue 的「目标」「完成标准」两节，别的节与模板注释不进")
    extra = set(rec["env"]) - set(policy["delegate"]["env_allow"]) - PLATFORM_ADDED
    t.check(not extra and "DELEGATE_SELFTEST_CANARY" not in rec["env"],
            f"{engine} 子进程只见白名单环境变量（env -i）" + (f"；多出 {sorted(extra)}" if extra else ""))
    t.check(rec["stdin_devnull"], f"{engine} 子进程 stdin 是 /dev/null（自测给 delegate 的是管道）")
    want = min(20, os.getpriority(os.PRIO_PROCESS, 0) + policy["machine"]["delegate_nice"])
    t.check(rec["prio"] >= want, f"{engine} 子进程优先级已降到 {want} 以下（实为 {rec['prio']}）")
    argv = rec["argv"]
    pairs = all(argv[i + 1] == "workspace-write" for i, a in enumerate(argv) if a == "-s") and any(
        a == "-c" and argv[i + 1] == "sandbox_workspace_write.network_access=false" for i, a in enumerate(argv[:-1]))
    t.check(all(m in argv for m in must) and (engine != "codex" or pairs), f"{engine} 沙箱与禁网参数写死在命令行")
    t.check(not rec["seen"] and rec["src_visible"], f"{engine} 快照里没有禁区文件、文件名与内容（含 final_paths）" + (f"：{rec['seen']}" if rec["seen"] else ""))
    t.check(rec["git_rc"] != 0, f"{engine} 快照里经 git 拿不到任何仓库（索引、对象库、历史）")
    t.check(not rec["cwd"].startswith((str(home), str(repo), str(ROOT))) and rec["parent_mode"] & 0o077 == 0,
            f"{engine} 工作目录在 ~/.modernize 与仓库之外、上级目录 0700")
    last = ledger(home)[-1]
    t.check(last.get("status") == "ok" and last.get("engine") == engine and last.get("changed_files") == 1, f"{engine} 台账有记录且通过")
    run_dir = home / ".modernize/psa/state/delegate/runs" / last["run_id"]
    diff = (run_dir / "diff.patch").read_text(encoding="utf-8")
    t.check("+changed" in diff and "src/a.txt" in diff, f"{engine} 产出含 diff")
    t.check("改了 src/a.txt" in (run_dir / "out.txt").read_text(encoding="utf-8"), f"{engine} 答复落盘到 out.txt")
    t.check(not Path(rec["cwd"]).exists() and not (run_dir / "base.git").exists(), f"{engine} 运行后快照与基点仓库已删")
    text = (home / ".modernize/psa/state/delegate/ledger.jsonl").read_text(encoding="utf-8")
    leak_free(text + (run_dir / "out.txt").read_text(encoding="utf-8") + diff, t, f"{engine} 台账、输出、diff")
    t.check(str(home) not in text, f"{engine} 台账不含本机私有路径")
    return last


def refused(t, repo, home, stubs, env, what, *args, expect_ledger=True):
    for d in (stubs, stubs.parent / "plain"):
        for s in ("codex", "pi"):
            (d / f"rec-{s}.json").unlink(missing_ok=True)
    n = len(ledger(home))
    rc, out = run(repo, env, *args)
    called = any((d / f"rec-{s}.json").exists() for d in (stubs, stubs.parent / "plain") for s in ("codex", "pi"))
    t.check(rc == 2 and not called, f"{what}：拒绝且不起子进程（退出 {rc}）")
    if expect_ledger:
        new = ledger(home)[n:]
        t.check(len(new) == 1 and new[0].get("status") == "rejected", f"{what}：台账记拒绝")
    return out


def with_policy(repo, policy, edit):
    p = json.loads(json.dumps(policy))
    edit(p)
    (repo / "policy.json").write_text(json.dumps(p, ensure_ascii=False), encoding="utf-8")


def main():
    if not DELEGATE.is_file():
        print("须在仓库根目录运行（找不到 scripts/delegate.py）", file=sys.stderr)
        return 1
    policy = json.loads((ROOT / "policy.json").read_text(encoding="utf-8"))
    t = T()
    tmp = Path(tempfile.mkdtemp(prefix="delegate-selftest-")).resolve()
    try:
        repo, home, stubs, plain, env = setup(tmp, policy)
        cx = happy(t, "codex", "codex", CODEX_MUST, repo, home, stubs, env, policy)
        ds = happy(t, "deepseek", "pi", PI_MUST, repo, home, stubs, env, policy)
        t.check(bool(cx and ds) and cx.get("tokens") == 1234 and ds.get("tokens") == "未知" and ds.get("cost") == "未知",
                "token 取到记数、取不到与费用记「未知」")
        base = ["run", "--engine", "codex", "--category", "机械改写", "--session", SESSION, "--card"]
        for what, num, extra, want in (("Issue 不存在", "404", {}, "取不到 Issue #404"), ("Issue 已关闭", "5", {}, "不是 open"),
                                       ("Issue 缺本会话标签（只有别的会话与挂起）", "6", {}, f"没有本会话标签「{LABEL}」"),
                                       ("Issue 正文缺「完成标准」节内容", "7", {}, "完成标准"), ("卡号不是 Issue 号", "T-1", {}, "Issue 号"),
                                       ("gh 输出不是 JSON", "8", {}, "取不到 Issue #8"), ("gh 输出不是 JSON 对象", "9", {}, "取不到 Issue #9"),
                                       ("gh 不可用（程序不存在）", "1", {"DELEGATE_GH": str(tmp / "no-such" / "gh")}, "gh 不可用"),
                                       ("gh 不可用（不可执行）", "1", {"DELEGATE_GH": str(tmp / "gh" / "issues.json")}, "gh 不可用")):
            out = refused(t, repo, home, stubs, {**env, **extra}, what, *base, num, "--scope", "src/a.txt")
            t.check(want in out and BODY not in out + json.dumps(ledger(home), ensure_ascii=False), f"{what}：报原因「{want}」，报错与台账不含 Issue 正文")
        for what, scope in (("范围是禁区文件", "scripts/hooks/x.py"), ("范围覆盖禁区文件", "scripts"),
                            ("范围是禁区通配（密钥文件）", "docs/SECRETS-sample.md"), ("范围出仓库", "../x"),
                            ("范围 ./ 前缀指向禁区", "./scripts/hooks/x.py"), ("范围大小写变体指向禁区", "SCRIPTS/Hooks/x.py"), ("范围大小写变体指向禁区内新文件", "Scripts/Hooks/new.py"),
                            ("范围含 . 段与连续斜杠指向禁区", "scripts/./hooks//x.py"), ("范围 .. 段绕回禁区", "src/../scripts/hooks/x.py"),
                            ("范围落在 review.final_paths（scripts/merge/）", "scripts/merge/m.py")):
            refused(t, repo, home, stubs, env, what, *base, "1", "--scope", scope)
        out = refused(t, repo, home, stubs, env, "卡里有密钥模式", *base, "3", "--scope", "src/a.txt")
        leak_free(out + json.dumps(ledger(home), ensure_ascii=False), t, "密钥卡被拒后的台账与报错")
        refused(t, repo, home, stubs, env, "类别不在试点", "run", "--engine", "deepseek", "--category", "设计", "--session", SESSION, "--card", "1", "--scope", "src/a.txt")
        penv = {**env, "PATH": f"{plain}:/usr/bin:/bin"}
        refused(t, repo, home, plain, penv, "real_run_enabled 为 false 时无桩标记的程序被拒", *base, "1", "--scope", "src/a.txt")
        with_policy(repo, policy, lambda p: p["delegate"].update(real_run_enabled=True))
        rc, _ = run(repo, penv, *base, "1", "--scope", "src/a.txt")
        t.check(rc == 0 and (plain / "rec-codex.json").exists(), "real_run_enabled 为 true 时同一程序放行（开关是唯一差别）")
        (repo / "policy.json").write_text(json.dumps(policy, ensure_ascii=False), encoding="utf-8")
        wt = tmp / "wt"   # 快照临时目录不得在仓库（含主检出）或 ~/.modernize 之内（调度 10-27，#44）；开关关着就核，真跑前必经
        subprocess.run(["git", "worktree", "add", "-q", str(wt)], cwd=repo, env=env, check=True, stdin=subprocess.DEVNULL)
        (tmp / "lnk").symlink_to(repo / "tmpin", target_is_directory=True)
        (tmp / "homelnk").symlink_to(home, target_is_directory=True)
        for d in (repo / "tmpin", home / ".modernize/tmpin"):
            d.mkdir(parents=True)
        for what, where, cwd, h in (("TMPDIR 在仓库内", repo / "tmpin", repo, home), ("TMPDIR 在 ~/.modernize 内", home / ".modernize/tmpin", repo, home),
                                    ("TMPDIR 经符号链接指进仓库", tmp / "lnk", repo, home), ("从 worktree 运行、TMPDIR 在主检出内", repo / "tmpin", wt, home),
                                    ("HOME 经符号链接、TMPDIR 在 ~/.modernize 内", home / ".modernize/tmpin", repo, tmp / "homelnk"),
                                    ("TMPDIR 写成仓库路径的大写变体", Path(str(repo / "tmpin").upper()), repo, home),
                                    ("TMPDIR 写成 ~/.modernize 路径的大写变体", Path(str(home / ".modernize/tmpin").upper()), repo, home),
                                    ("TMPDIR 加 /System/Volumes/Data 前缀指进仓库", Path("/System/Volumes/Data" + str(repo / "tmpin")), repo, home)):
            if not where.is_dir():   # 大写变体只在不分大小写的文件系统上、数据卷前缀只在 macOS 上指向同一目录；不成立的平台上这条不适用
                print(f"SKIP {what}（本机路径不指向同一目录）")
                continue
            where.mkdir(parents=True, exist_ok=True)
            out = refused(t, cwd, home, stubs, {**env, "TMPDIR": str(where), "HOME": str(h)}, what, *base, "1", "--scope", "src/a.txt")
            t.check("TMPDIR" in out and not any(where.iterdir()), f"{what}：报 TMPDIR，快照未落盘")
        (outside := tmp / "outside").mkdir()
        rc, _ = run(repo, {**env, "TMPDIR": str(outside)}, *base, "1", "--scope", "src/a.txt")
        t.check(rc == 0, f"TMPDIR 在仓库与 ~/.modernize 之外：放行（退出 {rc}）")
        subprocess.run(["git", "worktree", "remove", "--force", str(wt)], cwd=repo, env=env, check=True, stdin=subprocess.DEVNULL)
        with_policy(repo, policy, lambda p: p["delegate"]["pilot"]["categories"].append("复审"))
        refused(t, repo, home, stubs, env, "不外包类别（复审）", "run", "--engine", "deepseek", "--category", "复审", "--session", SESSION, "--card", "1", "--scope", "src/a.txt")
        breaks = [(f"policy 缺 delegate.{k}", lambda p, k=k: p["delegate"].pop(k))
                  for k in ("forbidden_paths", "forbidden_categories", "env_allow", "ledger", "real_run_enabled")]
        breaks += [("policy 缺 review.final_paths", lambda p: p["review"].pop("final_paths")),
                   ("machine.delegate_nice 为 null", lambda p: p["machine"].update(delegate_nice=None)),
                   ("real_run_enabled 写成字符串", lambda p: p["delegate"].update(real_run_enabled="false")),
                   ("engines.deepseek.tools 写成列表", lambda p: p["delegate"]["engines"]["deepseek"].update(tools=["read"]))]
        for what, edit in breaks:
            with_policy(repo, policy, edit)
            eng = "deepseek" if "deepseek" in what else "codex"
            refused(t, repo, home, stubs, env, f"{what} 失败关闭", "run", "--engine", eng, "--category", "机械改写", "--session", SESSION, "--card", "1",
                    "--scope", "src/a.txt", expect_ledger=False)
        (repo / "policy.json").write_text(json.dumps(policy, ensure_ascii=False), encoding="utf-8")
        rc, _ = run(repo, env, *base, "2", "--scope", "src/a.txt")
        last = ledger(home)[-1]
        t.check(rc == 1 and last.get("status") == "verify_failed" and "touch_forbidden" in last.get("verify", [])
                and "out_of_scope" in last.get("verify", []), "越界改禁区：机械验证不过、台账记 verify_failed")
        rc, _ = run(repo, env, *base, "4", "--scope", "src")
        last = ledger(home)[-1]
        t.check(rc == 1 and "out_of_scope" in last.get("verify", []), "范围外文件改名进范围：机械验证不过（改名两侧都核）")
        rc, _ = run(repo, env, "adopt", (cx or {}).get("run_id", "x"), "--adopted", "yes", "--rework", "1")
        t.check(rc == 0 and ledger(home)[-1].get("event") == "adopt", "adopt 记采纳与返工轮次")
        rc, out = run(repo, env, "summary")
        dline = next((l for l in out.splitlines() if " deepseek " in l), "")
        t.check(rc == 0 and " codex " in out and dline.count("（未知1）") == 2, "summary 按月按引擎汇总，未知不按 0 计")
        calls = [json.loads(l) for l in (tmp / "gh" / "calls.jsonl").read_text(encoding="utf-8").splitlines()]
        t.check(bool(calls) and all(c[:2] == ["issue", "view"] and c[3:] == ["--json", "state,labels,body"] for c in calls),
                f"gh 只以 issue view <号> --json state,labels,body 只读调用（共 {len(calls)} 次）")
        wts = subprocess.run(["git", "worktree", "list"], cwd=repo, env=env, capture_output=True, text=True).stdout
        t.check(len(wts.strip().splitlines()) == 1, "调用方仓库没有多出 worktree")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(f"delegate 自测：{'全过' if not t.fails else f'{t.fails} 项失败'}")
    return 1 if t.fails else 0


if __name__ == "__main__":
    sys.exit(main())
