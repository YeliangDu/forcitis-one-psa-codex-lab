#!/usr/bin/env python3
"""delegate：外部模型（Codex、DeepSeek 经 pi）的唯一入口（定稿 1.13 第 5 条，D33、D34、D37、D38、D40；O20）。

流程：准入 → 独立快照（系统临时目录下 0700 目录，只导出非禁区文件、不带 .git 与历史；基点仓库的 git 目录放在 runs_dir，快照之外）
→ 单一提示模板 → nice＋`env -i` 起子进程（stdin 指 /dev/null，沙箱与禁网参数写死）→ 产出落盘（输出、diff）→ 机械验证 → 台账。台账只记元数据（卡只记卡号），不记提示与输出全文；token、费用取不到记「未知」。
数值与枚举只读 policy.json 的 delegate 段、machine.delegate_nice、review.final_paths（全部并入禁区）；缺键或值不对即失败关闭。delegate.real_run_enabled 为 false 时只许带自测桩标记的程序，真引擎一律拒（调度 10-26 方案 A）。
未修（与 real_run_enabled 绑定，V16 前处理，见终审 79afae290 必改 2、3）：pi 一路没有系统沙箱，读写不限于快照；codex 的 workspace-write 只限写不限读，能按绝对路径读本机文件；两者都要实测 sandbox-exec 等外层限制，本次不调真程序做不了。
不收目录参数（b2b-plan 0.4）：在仓库根目录运行，读当前目录的 policy.json；卡从 GitHub Issue 只读获取（#49，调度 10-27），环境变量 DELEGATE_GH 指定 gh 只供自测。
调用方按 RULES 后台启动本程序；外部模型的凭据由本机配置提供（TEAM-34 现行做法），本程序不读不传。退出码：0 通过；1 运行或机械验证不过；2 准入拒绝或 policy 缺键；3 用法错。
用法：
  delegate.py run --engine codex|deepseek --category <试点类别> --card <Issue 号> --session <会话名> --scope <路径或通配>[,...] [--effort low|medium|high|xhigh]
  delegate.py adopt <运行号> --adopted yes|no [--rework N]
  delegate.py summary
"""
import argparse, contextlib, datetime, fnmatch, json, os, posixpath, re, shutil, signal, subprocess, sys, tarfile, tempfile, time, uuid
from pathlib import Path

CARD_ID, SESSION = re.compile(r"^[1-9][0-9]{0,8}$"), re.compile(r"^[a-z0-9][a-z0-9-]{0,63}$")   # 卡号即 Issue 号；会话名同 sessions/ 画像名
CARD_SECTIONS, GH_TIMEOUT, EFFORTS, STUB_MARK, UNKNOWN = ("目标", "完成标准"), 30, ("low", "medium", "high", "xhigh"), b"DELEGATE-SELFTEST-STUB", "未知"
# 写死的隔离参数（D40、TEAM-34 现行命令）；改动须终审，自测逐项核对。
CODEX_SAFETY = ("-s", "workspace-write", "-c", "sandbox_workspace_write.network_access=false", "-c", 'approval_policy="never"', "--ephemeral")
PI_SAFETY = ("--no-session", "--no-extensions", "--no-mcp", "--offline")
SECRET_PATTERNS = [re.compile(p) for p in (
    r"sk-[A-Za-z0-9_-]{20,}", r"gh[pousr]_[A-Za-z0-9]{30,}", r"github_pat_[A-Za-z0-9_]{30,}", r"AKIA[0-9A-Z]{16}",
    r"xox[abprs]-[A-Za-z0-9-]{10,}", r"-----BEGIN [A-Z ]*PRIVATE KEY-----",
    r"(?i)\b(?:api[_-]?key|secret|token|passw(?:or)?d)\b\s*[:=]\s*['\"]?[A-Za-z0-9_/+.=-]{16,}")]
NONPUBLIC_MARKERS = ("/.modernize/", "SECRETS")
PROMPT_TEMPLATE = """你是外包执行者，只按下面这张卡做事，卡上没写的不做。
约束：
- 只改这些路径（相对当前目录）：{scope}
- 不联网、不安装依赖、不读当前目录以外的文件；不输出任何凭据。
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
        if not isinstance(cur, dict) or cur.get(k) is None or cur.get(k) in ("", [], {}):
            raise Refuse(f"policy 缺 {path}（失败关闭）")
        cur = cur[k]
    return cur

def load_policy(engine=None):
    try:
        pol = json.loads(Path("policy.json").read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise Refuse(f"读不了当前目录的 policy.json：{e.__class__.__name__}")
    cfg = {k: need(pol, p) for k, p in (
        ("forbidden_paths", "delegate.forbidden_paths"), ("final_paths", "review.final_paths"), ("forbidden_categories", "delegate.forbidden_categories"),
        ("categories", "delegate.pilot.categories"), ("env_allow", "delegate.env_allow"), ("timeout", "delegate.timeout_minutes"),
        ("ledger", "delegate.ledger"), ("runs", "delegate.runs_dir"), ("real_run", "delegate.real_run_enabled"), ("nice", "machine.delegate_nice"))}
    if engine:
        cfg["model"] = need(pol, f"delegate.engines.{engine}.model")
        cfg["tools"] = need(pol, "delegate.engines.deepseek.tools") if engine == "deepseek" else "-"
    ok = (all(isinstance(cfg[k], list) and all(isinstance(x, str) and x for x in cfg[k])
              for k in ("forbidden_paths", "final_paths", "forbidden_categories", "categories", "env_allow"))
          and type(cfg["nice"]) is int and 0 < cfg["nice"] <= 20 and type(cfg["timeout"]) is int and cfg["timeout"] > 0
          and type(cfg["real_run"]) is bool and all(isinstance(cfg.get(k, ""), str) for k in ("model", "tools", "ledger", "runs")))
    if not ok:
        raise Refuse("policy delegate 段、machine.delegate_nice 或 review.final_paths 的值类型不对（失败关闭）")
    cfg["ledger"], cfg["runs"] = Path(os.path.expanduser(cfg["ledger"])), Path(os.path.expanduser(cfg["runs"]))
    return cfg

def forbidden(path, cfg):
    """禁区：delegate.forbidden_paths 通配或 review.final_paths 前缀；大小写不敏感（本机文件系统不区分大小写）。"""
    p = path.lower()
    return (any(fnmatch.fnmatchcase(p, g.lower()) for g in cfg["forbidden_paths"])
            or any(p.startswith(f.lower()) for f in cfg["final_paths"]))

def in_scope(path, scope):
    p = path.lower()
    return any(p == s.lower() or fnmatch.fnmatchcase(p, s.lower()) or p.startswith(s.lower().rstrip("/") + "/") for s in scope)

def norm_scope(s):
    """范围归一：去 ./、. 段与连续斜杠；绝对路径、~、反斜杠、.. 与空一律拒。"""
    n = posixpath.normpath(s.strip()) if s.strip() else ""
    if not n or s.strip().startswith(("/", "~")) or "\\" in s or n in (".", "..") or n.startswith("../"):
        raise Refuse("范围须是仓库内相对路径")
    return n

def leaks(text):
    """返回命中的模式编号（不返回命中内容，免得把密钥写进台账）。"""
    hits = [f"secret#{i}" for i, p in enumerate(SECRET_PATTERNS) if p.search(text)]
    return hits + [f"nonpublic#{i}" for i, m in enumerate(NONPUBLIC_MARKERS) if m in text]

def genv(gitdir=None, work=None):
    """git 子进程环境：去掉继承的 GIT_*，不读系统与全局配置，关 fsmonitor 与钩子；基点仓库显式指定 git 目录与工作树。"""
    env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    env.update(GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_COUNT="2", GIT_CONFIG_KEY_0="core.fsmonitor",
               GIT_CONFIG_VALUE_0="false", GIT_CONFIG_KEY_1="core.hooksPath", GIT_CONFIG_VALUE_1=os.devnull)
    if gitdir:
        env.update(GIT_DIR=str(gitdir), GIT_WORK_TREE=str(work))
    return env

def git(*args, cwd=".", env=None, check=True):
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, stdin=subprocess.DEVNULL, env=env or genv())
    if check and r.returncode:
        raise RuntimeError(f"git {args[0]} 失败（退出 {r.returncode}）")
    return r.stdout

def fetch_card(card, session):
    """从 Issue 只读取卡（#49，调度 10-27）：gh issue view 只取状态、标签与正文「目标」「完成标准」两节。
    取不到、gh 不可用、不是 open、无「会话:<本会话>」标签、缺节或节为空一律拒（失败关闭）；拒绝原因不带正文。"""
    try:
        r = subprocess.run([os.environ.get("DELEGATE_GH") or "gh", "issue", "view", card, "--json", "state,labels,body"],
                           capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=GH_TIMEOUT)
        issue = json.loads(r.stdout) if r.returncode == 0 else None
    except (OSError, subprocess.TimeoutExpired) as e:
        raise Refuse(f"gh 不可用，取不到 Issue #{card}（{e.__class__.__name__}，失败关闭）")
    except ValueError:
        raise Refuse(f"取不到 Issue #{card}（gh 输出不是 JSON，失败关闭）")
    if not isinstance(issue, dict):
        raise Refuse(f"取不到 Issue #{card}（gh 退出 {r.returncode} 或输出不是 JSON 对象，失败关闭）")
    if issue.get("state") != "OPEN":
        raise Refuse(f"Issue #{card} 不是 open，拒绝")
    if f"会话:{session}" not in {x.get("name") for x in (issue.get("labels") if isinstance(issue.get("labels"), list) else []) if isinstance(x, dict)}:
        raise Refuse(f"Issue #{card} 没有本会话标签「会话:{session}」，拒绝")
    body = re.sub(r"<!--.*?-->", "", issue.get("body") if isinstance(issue.get("body"), str) else "", flags=re.S)
    parts = {m.group(1): m.group(2).strip() for m in re.finditer(r"^##[ \t]+([^\n]*?)[ \t]*(?:\n|\Z)(.*?)(?=^##[ \t]|\Z)", body, re.M | re.S)}
    if [s for s in CARD_SECTIONS if not parts.get(s)]:
        raise Refuse(f"Issue #{card} 正文缺「{'」「'.join(s for s in CARD_SECTIONS if not parts.get(s))}」节或节为空，拒绝")
    return "\n".join(f"## {s}\n{parts[s]}" for s in CARD_SECTIONS)

def admit(args, cfg):
    """准入：类别、卡、范围、禁区、真跑开关；返回 (卡正文, 归一后的范围, 引擎程序)。"""
    if args.category not in cfg["categories"]:
        raise Refuse(f"类别不在 delegate.pilot.categories：{args.category}")
    if any(f in args.category or args.category in f for f in cfg["forbidden_categories"]):
        raise Refuse(f"类别属不外包（delegate.forbidden_categories）：{args.category}")
    if not CARD_ID.match(args.card) or not SESSION.match(args.session):
        raise Refuse("卡号须是 Issue 号（正整数），会话名只能是小写字母、数字、连字符")
    card = fetch_card(args.card, args.session)
    if leaks(card + args.scope):
        raise Refuse(f"卡或范围里有密钥模式或非公开材料标记：{'、'.join(leaks(card + args.scope))}")
    scope = [norm_scope(s) for s in args.scope.split(",") if s.strip()]
    if not scope:
        raise Refuse("--scope 为空")
    tracked = git("ls-tree", "-r", "-z", "--name-only", "HEAD").split("\0")
    for s in scope:
        if forbidden(s, cfg) or forbidden(s.rstrip("/") + "/x", cfg):
            raise Refuse(f"范围落在禁区（delegate.forbidden_paths 或 review.final_paths）：{s}")
        if n := sum(1 for p in tracked if p and in_scope(p, [s]) and forbidden(p, cfg)):
            raise Refuse(f"范围 {s} 覆盖禁区文件 {n} 个")
    exe = shutil.which("codex" if args.engine == "codex" else "pi")
    if not exe:
        raise Refuse(f"找不到 {args.engine} 的命令行程序")
    if not cfg["real_run"] and STUB_MARK not in Path(exe).read_bytes()[:4096]:
        raise Refuse("delegate.real_run_enabled 为 false：只许自测桩，真引擎须另批（V16）")
    roots = (git("rev-parse", "--show-toplevel").strip(), os.path.dirname(os.path.abspath(git("rev-parse", "--git-common-dir").strip())), os.path.expanduser("~/.modernize"))   # 本仓库、主检出（worktree 在其下）、~/.modernize
    ids = {(s.st_dev, s.st_ino) for s in map(os.stat, filter(os.path.exists, roots))}   # 快照所在临时目录及其各级上级都不得是它们（调度 10-27，#44）；比设备号与 inode，大小写变体、/System/Volumes/Data 前缀、符号链接都认得出（95b58f6c9 首轮、终审必改 1）
    if ids & {(s.st_dev, s.st_ino) for t in [Path(tempfile.gettempdir()).resolve()] for s in map(os.stat, [t, *t.parents])}:
        raise Refuse("快照临时目录（TMPDIR）在仓库或 ~/.modernize 之内，改到别处再跑")
    return card, scope, exe

def engine_argv(engine, exe, cfg, args, prompt, out_file, work):
    if engine == "codex":
        return [exe, "exec", "-m", cfg["model"], "-c", f"model_reasoning_effort={args.effort}", *CODEX_SAFETY,
                "-C", str(work), "-o", str(out_file), prompt]
    return [exe, "-p", *PI_SAFETY, "--model", cfg["model"], "--tools", cfg["tools"], prompt]

def isolate_cmd(argv, cfg):
    """nice 降优先级，`env -i` 清空环境，只带 delegate.env_allow 里的变量名（凭据由本机配置提供，见 TEAM-34）。"""
    keep = [f"{k}={os.environ[k]}" for k in cfg["env_allow"] if k in os.environ]
    return ["/usr/bin/nice", "-n", str(cfg["nice"]), "/usr/bin/env", "-i", *keep, *argv]

def snapshot(work, gitdir, cfg):
    """独立快照：git archive 流式解包，禁区文件不落盘；另建不带历史的基点仓库（git 目录在快照外），只写树对象作基点。"""
    p = subprocess.Popen(["git", "archive", "--format=tar", "HEAD"], stdout=subprocess.PIPE, stdin=subprocess.DEVNULL, env=genv())
    hidden, kw = 0, {"filter": "data"} if hasattr(tarfile, "data_filter") else {}
    with tarfile.open(fileobj=p.stdout, mode="r|") as tar:
        for m in tar:
            if forbidden(m.name, cfg):
                hidden += m.isfile()
                continue
            tar.extract(m, work, **kw)
    if p.wait():
        raise RuntimeError("git archive 失败")
    benv = genv(gitdir, work)
    git("init", "-q", cwd=work, env=benv)
    git("add", "-A", cwd=work, env=benv)
    return git("write-tree", cwd=work, env=benv).strip(), hidden

def run_engine(cmd, work, stdout_file, log_file, timeout_s):
    """codex 的最终答复由 -o 落盘，stdout 与 stderr 都进 engine.log；pi 的答复在 stdout，进 out.txt。"""
    with open(stdout_file, "wb") as out, open(log_file, "ab") as log:
        err = subprocess.STDOUT if stdout_file == log_file else log
        p = subprocess.Popen(cmd, cwd=work, stdin=subprocess.DEVNULL, stdout=out, stderr=err, start_new_session=True)
        try:
            rc, timed_out = p.wait(timeout=timeout_s), False
        except subprocess.TimeoutExpired:
            rc, timed_out = None, True
        with contextlib.suppress(ProcessLookupError, PermissionError):
            os.killpg(p.pid, signal.SIGKILL)
        p.wait()
        return rc, timed_out

def verify(work, benv, base, scope, cfg, texts):
    """机械验证：改动（关改名检测，两侧路径都算）只在范围内、不碰禁区；diff 无空白错；.py 能编译、.json 能解析；无泄露。"""
    git("add", "-A", cwd=work, env=benv)
    tree = git("write-tree", cwd=work, env=benv).strip()
    changed = [p for p in git("diff", "--no-renames", "--name-only", "-z", base, tree, cwd=work, env=benv).split("\0") if p]
    diff = git("diff", "--no-renames", "--binary", base, tree, cwd=work, env=benv)
    fails = ["touch_forbidden"] * any(forbidden(p, cfg) for p in changed) + ["out_of_scope"] * any(not in_scope(p, scope) for p in changed)
    if git("diff", "--no-renames", "--check", base, tree, cwd=work, env=benv, check=False).strip():
        fails.append("whitespace")
    for p in changed:
        f = work / p
        if f.is_file() and p.endswith(".py"):
            r = subprocess.run([sys.executable, "-I", "-c", "import sys;compile(open(sys.argv[1],'rb').read(),sys.argv[1],'exec')", str(f)],
                               capture_output=True, stdin=subprocess.DEVNULL)
            fails += [f"py_compile#{changed.index(p)}"] * bool(r.returncode)
        elif f.is_file() and p.endswith(".json"):
            try:
                json.loads(f.read_text(encoding="utf-8"))
            except ValueError:
                fails.append(f"json#{changed.index(p)}")
    for name, text in (("diff", diff), *texts):
        fails += [f"{name}:{h}" for h in leaks(text)]
    return changed, diff, fails

def append_ledger(cfg, rec):
    cfg["ledger"].parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with open(cfg["ledger"], "a", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")

def tokens_in(log_text):
    m = re.search(r"tokens used\D{0,20}([\d,]+)", log_text, re.I)
    return int(m.group(1).replace(",", "")) if m else UNKNOWN

def cmd_run(args):
    run_id = datetime.datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:6]
    safe = lambda v: "<已屏蔽：含密钥模式或非公开标记>" if leaks(v) else v
    base_rec = {"ts": now(), "event": "run", "run_id": run_id, "engine": args.engine, "category": safe(args.category), "card": safe(args.card), "scope": safe(args.scope)}
    cfg = load_policy(args.engine)
    try:
        if git("rev-parse", "--show-toplevel").strip() != str(Path.cwd().resolve()):
            raise Refuse("须在仓库根目录运行")
        card, scope, exe = admit(args, cfg)
        head = git("rev-parse", "HEAD").strip()
    except (Refuse, RuntimeError, OSError) as e:
        append_ledger(cfg, {**base_rec, "status": "rejected", "reason": str(e) if not isinstance(e, OSError) else e.__class__.__name__})
        print(f"delegate：拒绝——{e}", file=sys.stderr)
        return 2
    cfg["runs"].mkdir(parents=True, exist_ok=True, mode=0o700)
    (run_dir := cfg["runs"] / run_id).mkdir(mode=0o700)
    out_file, log_file, gitdir = run_dir / "out.txt", run_dir / "engine.log", run_dir / "base.git"
    tmp = Path(tempfile.mkdtemp(prefix="delegate-")).resolve()
    (work := tmp / "work").mkdir(mode=0o700)
    prompt = PROMPT_TEMPLATE.format(scope="、".join(scope), category=args.category, card_id=args.card, card=card)
    rec = {**base_rec, "scope": "、".join(scope), "model": cfg["model"], "effort": args.effort if args.engine == "codex" else None,
           "base": head, "nice": cfg["nice"], "prompt_chars": len(prompt), "tokens": UNKNOWN, "cost": UNKNOWN, "rework": 0, "adopted": None}
    try:
        base, rec["hidden_files"] = snapshot(work, gitdir, cfg)
        cmd = isolate_cmd(engine_argv(args.engine, exe, cfg, args, prompt, out_file, work), cfg)
        t0 = time.monotonic()
        rc, timed_out = run_engine(cmd, work, log_file if args.engine == "codex" else out_file, log_file, cfg["timeout"] * 60)
        rec["seconds"] = round(time.monotonic() - t0, 1)
        log_text = log_file.read_text(encoding="utf-8", errors="replace") if log_file.exists() else ""
        out_text = out_file.read_text(encoding="utf-8", errors="replace") if out_file.exists() else ""
        rec["tokens"] = tokens_in(log_text)
        changed, diff, fails = verify(work, genv(gitdir, work), base, scope, cfg, [("out", out_text)])
        fails += [f"log:{h}" for h in leaks(log_text) if h.startswith("secret")]
        (run_dir / "diff.patch").write_text(diff, encoding="utf-8")
        fails[:0] = ["timeout"] if timed_out else [f"engine_exit:{rc}"] if rc else []
        rec.update(rc=rc, changed_files=len(changed), diff_lines=diff.count("\n"), out_chars=len(out_text), verify=fails, status="ok" if not fails else "verify_failed")
    except (RuntimeError, OSError, tarfile.TarError) as e:
        rec.update(status="error", reason=e.__class__.__name__ if isinstance(e, OSError) else str(e))
    finally:
        rec.setdefault("status", "error")
        shutil.rmtree(tmp, ignore_errors=True)
        shutil.rmtree(gitdir, ignore_errors=True)
        append_ledger(cfg, rec)
    print(f"delegate：{rec['status']} 运行号 {run_id}；产出 {run_dir}（out.txt、diff.patch）"
          + (f"；未过：{'、'.join(rec.get('verify') or [rec.get('reason', '')])}" if rec["status"] != "ok" else ""))
    return 0 if rec["status"] == "ok" else 1

def read_ledger(cfg):
    text = cfg["ledger"].read_text(encoding="utf-8") if cfg["ledger"].exists() else ""
    return [json.loads(l) for l in text.splitlines() if l.strip()]

def cmd_adopt(args):
    cfg = load_policy()
    if not any(r.get("run_id") == args.run_id and r.get("event") == "run" for r in read_ledger(cfg)):
        print(f"delegate：台账里没有运行号 {args.run_id}", file=sys.stderr)
        return 3
    append_ledger(cfg, {"ts": now(), "event": "adopt", "run_id": args.run_id, "adopted": args.adopted == "yes", "rework": args.rework})
    print(f"delegate：已记 {args.run_id} 采纳={args.adopted} 返工轮次={args.rework}")
    return 0

def cmd_summary(_args):
    """按月、按引擎汇总（D34：DeepSeek 只记账，按月汇总费用）；token、费用未知的单列次数，不按 0 计。"""
    entries = read_ledger(load_policy())
    rows, adopt = {}, {r["run_id"]: r for r in entries if r.get("event") == "adopt"}
    for r in entries:
        if r.get("event") != "run":
            continue
        s = rows.setdefault((r["ts"][:7], r["engine"]), {"runs": 0, "ok": 0, "rejected": 0, "seconds": 0.0, "tokens": 0,
                                                           "tokens?": 0, "cost": 0.0, "cost?": 0, "adopted": 0, "rework": 0})
        for k, v in (("runs", 1), ("ok", r.get("status") == "ok"), ("rejected", r.get("status") == "rejected"), ("seconds", r.get("seconds") or 0)):
            s[k] += v
        for k in ("tokens", "cost") if r.get("status") != "rejected" else ():
            v = r.get(k)
            if isinstance(v, (int, float)) and not isinstance(v, bool):
                s[k] += v
            else:
                s[k + "?"] += 1
        a = adopt.get(r.get("run_id"))
        s["adopted"] += bool(a and a["adopted"])
        s["rework"] += (a or {}).get("rework", 0)
    print("月份 引擎 运行 通过 拒绝 用时秒 token（未知次数） 费用（未知次数） 采纳 返工")
    for (m, e), s in sorted(rows.items()):
        print(m, e, s["runs"], s["ok"], s["rejected"], round(s["seconds"]), f"{s['tokens']}（{UNKNOWN}{s['tokens?']}）",
              f"{round(s['cost'], 2)}（{UNKNOWN}{s['cost?']}）", s["adopted"], s["rework"])
    return 0

def main(argv=None):
    ap = argparse.ArgumentParser(description="外部模型唯一入口（O20）")
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("--engine", required=True, choices=("codex", "deepseek"))
    for name in ("--category", "--card", "--scope", "--session"):
        r.add_argument(name, required=True)
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
