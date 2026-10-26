#!/usr/bin/env python3
"""delegate：外部模型（Codex、DeepSeek 经 pi）的唯一入口（定稿 1.13 第 5 条，D33、D34、D37、D38、D40；O20）。

流程：准入 → 独立 worktree（禁区文件先移出）→ 单一提示模板 → nice＋`env -i` 起子进程（stdin 指 /dev/null，沙箱与禁网参数写死）
→ 产出落盘（输出、diff）→ 机械验证 → 台账。台账只记元数据，不记提示与输出全文。
数值与枚举只读 policy.json 的 delegate 段与 machine.delegate_nice；缺键或值不对即失败关闭（退出 2，不起子进程）。
不收目录参数（b2b-plan 0.4）：在仓库根目录运行，读当前目录的 policy.json 与 docs/modernization/tasks/<卡号>.md。
调用方按 RULES 后台启动本程序；外部模型的凭据由本机配置提供（TEAM-34 现行做法），本程序不读不传。
退出码：0 通过；1 运行或机械验证不过；2 准入拒绝或 policy 缺键；3 用法错。
用法：
  delegate.py run --engine codex|deepseek --category <试点类别> --card <卡号> --scope <路径或通配>[,...] [--effort low|medium|high|xhigh]
  delegate.py adopt <运行号> --adopted yes|no [--rework N]
  delegate.py summary
"""
import argparse, datetime, fnmatch, json, os, re, shutil, signal, subprocess, sys, time, uuid
from pathlib import Path

TASKS_DIR = "docs/modernization/tasks"
CARD_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,80}$")
EFFORTS = ("low", "medium", "high", "xhigh")
# 写死的隔离参数（D40、TEAM-34 现行命令）；改动须终审，自测逐项核对。
CODEX_SAFETY = ("-s", "workspace-write", "-c", "sandbox_workspace_write.network_access=false",
                "-c", 'approval_policy="never"', "--ephemeral")
PI_SAFETY = ("--no-session", "--no-extensions", "--no-mcp", "--offline")
SECRET_PATTERNS = [re.compile(p) for p in (
    r"sk-[A-Za-z0-9_-]{20,}", r"gh[pousr]_[A-Za-z0-9]{30,}", r"github_pat_[A-Za-z0-9_]{30,}", r"AKIA[0-9A-Z]{16}",
    r"xox[abprs]-[A-Za-z0-9-]{10,}", r"-----BEGIN [A-Z ]*PRIVATE KEY-----",
    r"(?i)\b(?:api[_-]?key|secret|token|passw(?:or)?d)\b\s*[:=]\s*['\"]?[A-Za-z0-9_/+.=-]{16,}")]
NONPUBLIC_MARKERS = ("/.modernize/", "SECRETS")
PROMPT_TEMPLATE = """你是外包执行者，只按下面这张卡做事，卡上没写的不做。
约束：
- 只改这些路径（相对当前目录）：{scope}
- 不联网、不安装依赖、不读当前目录以外的文件、不运行会改动 git 历史的命令；不输出任何凭据。
- 完成后用中文简要列出改了哪些文件、为什么；做不到就说明原因，不要猜。
类别：{category}
—— 卡 {card_id} ——
{card}
"""


class Refuse(Exception):
    """准入拒绝或 policy 缺键：退出 2，不起子进程。"""


def now():
    return datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).isoformat()


def need(d, path):
    """按点号路径取 policy 值；缺键、null 或空即拒绝（失败关闭）。"""
    cur = d
    for k in path.split("."):
        if not isinstance(cur, dict) or cur.get(k) in (None, "", [], {}):
            raise Refuse(f"policy 缺 {path}（失败关闭）")
        cur = cur[k]
    return cur


def load_policy(engine=None):
    try:
        pol = json.loads(Path("policy.json").read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise Refuse(f"读不了当前目录的 policy.json：{e.__class__.__name__}")
    cfg = {"forbidden_paths": need(pol, "delegate.forbidden_paths"),
           "forbidden_categories": need(pol, "delegate.forbidden_categories"),
           "categories": need(pol, "delegate.pilot.categories"),
           "env_allow": need(pol, "delegate.env_allow"),
           "timeout": need(pol, "delegate.timeout_minutes"),
           "ledger": Path(os.path.expanduser(need(pol, "delegate.ledger"))),
           "runs": Path(os.path.expanduser(need(pol, "delegate.runs_dir"))),
           "nice": need(pol, "machine.delegate_nice")}
    if engine:
        cfg["model"] = need(pol, f"delegate.engines.{engine}.model")
        if engine == "deepseek":
            cfg["tools"] = need(pol, "delegate.engines.deepseek.tools")
    ok = (all(isinstance(cfg[k], list) and all(isinstance(x, str) and x for x in cfg[k])
              for k in ("forbidden_paths", "forbidden_categories", "categories", "env_allow"))
          and type(cfg["nice"]) is int and 0 < cfg["nice"] <= 20
          and type(cfg["timeout"]) is int and cfg["timeout"] > 0)
    if not ok:
        raise Refuse("policy delegate 段或 machine.delegate_nice 的值类型不对（失败关闭）")
    return cfg


def forbidden(path, cfg):
    return any(fnmatch.fnmatchcase(path, g) for g in cfg["forbidden_paths"])


def in_scope(path, scope):
    return any(path == s or fnmatch.fnmatchcase(path, s) or path.startswith(s.rstrip("/") + "/") for s in scope)


def leaks(text):
    """返回命中的模式编号（不返回命中内容，免得把密钥写进台账）。"""
    hits = [f"secret#{i}" for i, p in enumerate(SECRET_PATTERNS) if p.search(text)]
    return hits + [f"nonpublic#{i}" for i, m in enumerate(NONPUBLIC_MARKERS) if m in text]


def git(*args, cwd=".", check=True):
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, stdin=subprocess.DEVNULL)
    if check and r.returncode:
        raise RuntimeError(f"git {args[0]} 失败（退出 {r.returncode}）")
    return r.stdout


def admit(args, cfg):
    """准入：类别、卡、范围、禁区；返回 (卡正文, 范围列表)。"""
    if args.category not in cfg["categories"]:
        raise Refuse(f"类别不在 delegate.pilot.categories：{args.category}")
    if any(f in args.category or args.category in f for f in cfg["forbidden_categories"]):
        raise Refuse(f"类别属不外包（delegate.forbidden_categories）：{args.category}")
    if not CARD_ID.match(args.card):
        raise Refuse("卡号只能是字母、数字、点、下划线、连字符")
    card_path = f"{TASKS_DIR}/{args.card}.md"
    if forbidden(card_path, cfg):
        raise Refuse("卡文件在禁区")
    try:
        card = Path(card_path).read_text(encoding="utf-8")
    except OSError:
        raise Refuse(f"找不到卡 {card_path}（须在仓库根目录运行）")
    if leaks(card + args.scope):
        raise Refuse(f"卡或范围里有密钥模式或非公开材料标记：{'、'.join(leaks(card + args.scope))}")
    scope = [s.strip() for s in args.scope.split(",") if s.strip()]
    if not scope:
        raise Refuse("--scope 为空")
    tracked = git("ls-files", "-z").split("\0")
    for s in scope:
        if s.startswith(("/", "~")) or ".." in Path(s).parts or "\\" in s:
            raise Refuse(f"范围须是仓库内相对路径：{s}")
        if forbidden(s, cfg) or forbidden(s.rstrip("/") + "/x", cfg):
            raise Refuse(f"范围落在禁区（delegate.forbidden_paths）：{s}")
        hit = [p for p in tracked if p and in_scope(p, [s]) and forbidden(p, cfg)]
        if hit:
            raise Refuse(f"范围 {s} 覆盖禁区文件 {len(hit)} 个，如 {hit[0]}")
    return card, scope


def engine_argv(engine, cfg, args, prompt, out_file, wt):
    exe = shutil.which("codex" if engine == "codex" else "pi")
    if not exe:
        raise Refuse(f"找不到 {engine} 的命令行程序")
    if engine == "codex":
        return [exe, "exec", "-m", cfg["model"], "-c", f"model_reasoning_effort={args.effort}", *CODEX_SAFETY,
                "-C", str(wt), "-o", str(out_file), prompt]
    return [exe, "-p", *PI_SAFETY, "--model", cfg["model"], "--tools", cfg["tools"], prompt]


def isolate_cmd(argv, cfg):
    """nice 降优先级，`env -i` 清空环境，只带 delegate.env_allow 里的变量名（凭据由本机配置提供，见 TEAM-34）。"""
    keep = [f"{k}={os.environ[k]}" for k in cfg["env_allow"] if k in os.environ]
    return ["/usr/bin/nice", "-n", str(cfg["nice"]), "/usr/bin/env", "-i", *keep, *argv]


def prepare_worktree(wt, cfg):
    """独立 worktree：基于 HEAD，禁区文件先删掉不给外部模型看；记下此时的树作 diff 基点（只写树对象，不提交、不触发钩子）。"""
    git("worktree", "add", "--detach", str(wt), "HEAD")
    removed = 0
    for p in git("ls-files", "-z", cwd=wt).split("\0"):
        if p and forbidden(p, cfg):
            (wt / p).unlink(missing_ok=True)
            removed += 1
    git("add", "-A", cwd=wt)
    return git("write-tree", cwd=wt).strip(), removed


def run_engine(cmd, wt, stdout_file, log_file, timeout_s):
    """codex 的最终答复由 -o 落盘，stdout 与 stderr 都进 engine.log；pi 的答复在 stdout，进 out.txt。"""
    with open(stdout_file, "wb") as out, open(log_file, "ab") as log:
        err = subprocess.STDOUT if stdout_file == log_file else log
        p = subprocess.Popen(cmd, cwd=wt, stdin=subprocess.DEVNULL, stdout=out, stderr=err, start_new_session=True)
        try:
            return p.wait(timeout=timeout_s), False
        except subprocess.TimeoutExpired:
            os.killpg(p.pid, signal.SIGKILL)
            p.wait()
            return None, True


def verify(wt, base, scope, cfg, texts):
    """机械验证：改动只在范围内、不碰禁区；diff 无空白错；改到的 .py 能编译、.json 能解析；输出与 diff 无密钥模式与非公开标记。"""
    git("add", "-A", cwd=wt)
    tree = git("write-tree", cwd=wt).strip()
    changed = [p for p in git("diff", "--name-only", "-z", base, tree, cwd=wt).split("\0") if p]
    diff = git("diff", "--binary", base, tree, cwd=wt)
    fails = []
    if any(forbidden(p, cfg) for p in changed):
        fails.append("touch_forbidden")
    if any(not in_scope(p, scope) for p in changed):
        fails.append("out_of_scope")
    if git("diff", "--check", base, tree, cwd=wt, check=False).strip():
        fails.append("whitespace")
    for p in changed:
        f = wt / p
        if not f.is_file():
            continue
        if p.endswith(".py"):
            r = subprocess.run([sys.executable, "-I", "-c", "import sys;compile(open(sys.argv[1],'rb').read(),sys.argv[1],'exec')", str(f)],
                               capture_output=True, stdin=subprocess.DEVNULL)
            if r.returncode:
                fails.append(f"py_compile:{p}")
        elif p.endswith(".json"):
            try:
                json.loads(f.read_text(encoding="utf-8"))
            except ValueError:
                fails.append(f"json:{p}")
    for name, text in (("diff", diff), *texts):
        fails += [f"{name}:{h}" for h in leaks(text)]
    return changed, diff, fails


def append_ledger(cfg, rec):
    cfg["ledger"].parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with open(cfg["ledger"], "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")


def tokens_in(log_text):
    m = re.search(r"tokens used\D{0,20}([\d,]+)", log_text, re.I)
    return int(m.group(1).replace(",", "")) if m else None


def cmd_run(args):
    run_id = datetime.datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6]
    safe = lambda v: "<已屏蔽：含密钥模式或非公开标记>" if leaks(v) else v
    base_rec = {"ts": now(), "event": "run", "run_id": run_id, "engine": args.engine, "category": safe(args.category),
                "card": safe(args.card), "scope": safe(args.scope)}
    cfg = load_policy(args.engine)
    try:
        if git("rev-parse", "--show-toplevel").strip() != str(Path.cwd().resolve()):
            raise Refuse("须在仓库根目录运行")
        card, scope = admit(args, cfg)
        engine_argv(args.engine, cfg, args, "", Path("-"), Path("-"))
    except (Refuse, RuntimeError) as e:
        append_ledger(cfg, {**base_rec, "status": "rejected", "reason": str(e)})
        print(f"delegate：拒绝——{e}", file=sys.stderr)
        return 2
    cfg["runs"].mkdir(parents=True, exist_ok=True, mode=0o700)
    run_dir = cfg["runs"] / run_id
    run_dir.mkdir(mode=0o700)
    out_file, log_file, wt = run_dir / "out.txt", run_dir / "engine.log", run_dir / "wt"
    prompt = PROMPT_TEMPLATE.format(scope="、".join(scope), category=args.category, card_id=args.card, card=card)
    rec = {**base_rec, "model": cfg["model"], "effort": args.effort if args.engine == "codex" else None,
           "base": git("rev-parse", "HEAD").strip(), "nice": cfg["nice"], "prompt_chars": len(prompt),
           "tokens": None, "cost": None, "rework": 0, "adopted": None}
    try:
        base, rec["hidden_files"] = prepare_worktree(wt, cfg)
        cmd = isolate_cmd(engine_argv(args.engine, cfg, args, prompt, out_file, wt), cfg)
        t0 = time.monotonic()
        rc, timed_out = run_engine(cmd, wt, log_file if args.engine == "codex" else out_file, log_file, cfg["timeout"] * 60)
        rec["seconds"] = round(time.monotonic() - t0, 1)
        log_text = log_file.read_text(encoding="utf-8", errors="replace") if log_file.exists() else ""
        out_text = out_file.read_text(encoding="utf-8", errors="replace") if out_file.exists() else ""
        rec["tokens"] = tokens_in(log_text)
        changed, diff, fails = verify(wt, base, scope, cfg, [("out", out_text)])
        fails += [f"log:{h}" for h in leaks(log_text) if h.startswith("secret")]
        (run_dir / "diff.patch").write_text(diff, encoding="utf-8")
        if timed_out:
            fails.insert(0, "timeout")
        elif rc:
            fails.insert(0, f"engine_exit:{rc}")
        rec.update(rc=rc, changed_files=len(changed), diff_lines=diff.count("\n"), out_chars=len(out_text),
                   verify=fails, status="ok" if not fails else "verify_failed")
    except (RuntimeError, OSError, Refuse) as e:
        rec.update(status="error", reason=f"{e.__class__.__name__}：{e}" if isinstance(e, OSError) else str(e))
    finally:
        rec.setdefault("status", "error")
        if wt.exists():
            git("worktree", "remove", "--force", str(wt), check=False)
            shutil.rmtree(wt, ignore_errors=True)
        git("worktree", "prune", check=False)
        append_ledger(cfg, rec)
    print(f"delegate：{rec['status']} 运行号 {run_id}；产出 {run_dir}（out.txt、diff.patch）"
          + (f"；未过：{'、'.join(rec.get('verify') or [rec.get('reason', '')])}" if rec["status"] != "ok" else ""))
    return 0 if rec["status"] == "ok" else 1


def read_ledger(cfg):
    if not cfg["ledger"].exists():
        return []
    return [json.loads(l) for l in cfg["ledger"].read_text(encoding="utf-8").splitlines() if l.strip()]


def cmd_adopt(args):
    cfg = load_policy()
    if not any(r.get("run_id") == args.run_id and r.get("event") == "run" for r in read_ledger(cfg)):
        print(f"delegate：台账里没有运行号 {args.run_id}", file=sys.stderr)
        return 3
    append_ledger(cfg, {"ts": now(), "event": "adopt", "run_id": args.run_id, "adopted": args.adopted == "yes",
                        "rework": args.rework})
    print(f"delegate：已记 {args.run_id} 采纳={args.adopted} 返工轮次={args.rework}")
    return 0


def cmd_summary(_args):
    """按月、按引擎汇总（D34：DeepSeek 只记账，按月汇总费用）。"""
    entries = read_ledger(load_policy())
    rows, adopt = {}, {r["run_id"]: r for r in entries if r.get("event") == "adopt"}
    for r in entries:
        if r.get("event") != "run":
            continue
        k = (r["ts"][:7], r["engine"])
        s = rows.setdefault(k, {"runs": 0, "ok": 0, "rejected": 0, "seconds": 0.0, "tokens": 0, "cost": 0.0, "adopted": 0, "rework": 0})
        s["runs"] += 1
        s["ok"] += r.get("status") == "ok"
        s["rejected"] += r.get("status") == "rejected"
        s["seconds"] += r.get("seconds") or 0
        s["tokens"] += r.get("tokens") or 0
        s["cost"] += r.get("cost") or 0
        a = adopt.get(r.get("run_id"))
        s["adopted"] += bool(a and a["adopted"])
        s["rework"] += (a or {}).get("rework", 0)
    print("月份 引擎 运行 通过 拒绝 用时秒 token 费用 采纳 返工")
    for (m, e), s in sorted(rows.items()):
        print(m, e, s["runs"], s["ok"], s["rejected"], round(s["seconds"]), s["tokens"], round(s["cost"], 2), s["adopted"], s["rework"])
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser(description="外部模型唯一入口（O20）")
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--engine", required=True, choices=("codex", "deepseek"))
    r.add_argument("--category", required=True)
    r.add_argument("--card", required=True)
    r.add_argument("--scope", required=True)
    r.add_argument("--effort", default="medium", choices=EFFORTS)
    a = sub.add_parser("adopt")
    a.add_argument("run_id")
    a.add_argument("--adopted", required=True, choices=("yes", "no"))
    a.add_argument("--rework", type=int, default=0)
    sub.add_parser("summary")
    try:
        args = ap.parse_args(argv)
    except SystemExit as e:
        return 3 if e.code else 0
    try:
        return {"run": cmd_run, "adopt": cmd_adopt, "summary": cmd_summary}[args.cmd](args)
    except Refuse as e:
        print(f"delegate：拒绝——{e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
