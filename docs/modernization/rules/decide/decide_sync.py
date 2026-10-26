#!/usr/bin/env python3
"""决定链路一条龙：读用户在确认页的提交 → 对账 → 生成 intent 片段 → 打印收件箱回写所需的 ArtifactData 批量写入。
用法：
  python3 docs/modernization/rules/decide/decide_sync.py <单子> <out_dir> --category=<类别> --owner=<负责会话> --paths=<路径前缀|无> --supersedes=<被取代片段|无> [--session=one-rules] [--inbox-id=<收件箱事项 id>] [--no-intent]
  python3 docs/modernization/rules/decide/decide_sync.py --check <收件箱 out_dir> <单子>=<确认页 out_dir> [<单子>=<确认页 out_dir> ...]
    对账检查（调度报「待答」前先跑）：读收件箱导出与各确认页导出，报出「收件箱仍是 pending，但确认页已提交」的事项，以及确认页提交后又改过、须重新提交的。
    事项对应哪张单子：事项里写 sheet 字段，或事项的 link 等于 README 页面表里该单子的链接；对不上的待定事项不报。有不一致时返回码 1。
前提：已用 ArtifactData list 把该确认页的 answers、answers-submit 存到 <out_dir>。
做三件事：
1. 对账：调用 export_answers 的逻辑，有提交后又改过的题则报错退出；
2. 每个提交编号生成一条 intent 片段（用户原话 = 题号 + 所选项文字 + 说明，落点指向答复文件 rules/decide/answers/<单子>.json），文件名打印出来；
   new_intent 的四个必填项由调用方给：--category、--owner、--paths、--supersedes，缺任一即拒并列缺项，原样转给 new_intent（取值见其脚本头；G3 落地后由页面元数据填这三个参数）；只导出不生成 intent 时用 --no-intent；
3. 打印 inbox 事项改为已定所需的 ArtifactData update 内容（status、answer、decidedAt、intent 文件名），由会话用 ArtifactData 执行：
   页面是各自独立的发布件，确认页不能直接写收件箱，所以回写由会话完成，并在报告里附这条命令的输出。
不自动改规则卡、ADR；转写仍由会话执行并复核。
幂等：同一提交编号已有 intent 片段（片段正文含「提交 <编号>」）时不重复生成。原话里的「|」按 new_intent 的要求转义为「\\|」，不改动其他字符；生成失败（返回码非 0 或没有产出片段）即退出，不打印成功。
覆盖检查：存在 sheets/<单子>.json 时，核对提交是否覆盖全部题、所选序号对应的选项文字是否仍与单子一致，不一致报错退出（发布后改过选项会造成序号错位）。"""
import datetime, glob, json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import export_answers  # noqa: E402

if "--check" in sys.argv:
    import glob as _g, re as _re
    rest = [a for a in sys.argv[1:] if a != "--check"]
    if len(rest) < 2 or any("=" not in a for a in rest[1:]):
        sys.exit(__doc__)
    inbox_dir, pages = rest[0], dict(a.split("=", 1) for a in rest[1:])
    urls = {}
    for line in open(os.path.join(HERE, "README.md"), encoding="utf-8"):
        m = _re.match(r"^\| (\w+) .*?(https://claude\.ai/artifact/[\w-]+)", line)
        if m:
            urls[m.group(2)] = m.group(1)
    state = {}
    for sheet_id, d in pages.items():
        try:
            out, stale = export_answers.export(sheet_id, d)
            state[sheet_id] = ("stale", stale) if stale else ("submitted", sorted({v["submission"] for v in out.values()})) if out else ("none", [])
        except Exception as e:  # 导出目录读不到等
            state[sheet_id] = ("error", [str(e)])
    bad = 0
    for f in sorted(_g.glob(os.path.join(inbox_dir, "inbox", "*.json"))):
        doc = export_answers.load(f)
        if (doc.get("status") or "pending") != "pending":
            continue
        sh = doc.get("sheet") or urls.get((doc.get("link") or "").split("?")[0].rstrip("/"))
        if not sh or sh not in state:
            continue
        kind, info = state[sh]
        name = os.path.basename(f)[:-5]
        if kind == "submitted":
            print(f"不一致：收件箱事项 {name} 仍是 pending，确认页 {sh} 已提交（编号 {'、'.join(info)}）→ 跑 decide_sync 回写"); bad += 1
        elif kind == "stale":
            print(f"待重新提交：收件箱事项 {name}；确认页 {sh} 提交后改过：{'、'.join(info)}"); bad += 1
        elif kind == "error":
            print(f"无法核对：收件箱事项 {name}；确认页 {sh} 的导出读取失败：{info[0]}"); bad += 1
    print("检查通过：收件箱与确认页一致" if not bad else f"共 {bad} 处不一致")
    sys.exit(1 if bad else 0)
args = [a for a in sys.argv[1:] if not a.startswith("--")]
opts = dict(a[2:].split("=", 1) if "=" in a else (a[2:], True) for a in sys.argv[1:] if a.startswith("--"))
if len(args) != 2:
    sys.exit(__doc__)
sheet, base = args
passed = [f"--{k}={opts[k]}" for k in ("category", "owner", "paths", "supersedes") if isinstance(opts.get(k), str) and opts[k]]
if not opts.get("no-intent") and len(passed) < 4:
    sys.exit(f"缺必填项：{'、'.join(f'--{k}' for k in ('category', 'owner', 'paths', 'supersedes') if not any(p.startswith(f'--{k}=') for p in passed))}（原样转给 new_intent，取值与写法见其脚本头；不生成 intent 时用 --no-intent）")
out, stale = export_answers.export(sheet, base)
if stale:
    sys.exit(f"对账失败：这些题提交后又改过，请用户重新提交：{'、'.join(stale)}")
if not out:
    sys.exit("没有已提交的答复")
sheet_file = os.path.join(HERE, "sheets", f"{sheet}.json")
if os.path.exists(sheet_file):
    items = {f"{sheet}~{i['no']}": i for i in json.load(open(sheet_file, encoding="utf-8"))["items"]}
    missing = sorted(set(items) - set(out))
    if missing:
        sys.exit(f"提交没有覆盖全部题，缺：{'、'.join(m.split('~', 1)[1] for m in missing)}；请用户补提交")
    for k, v in out.items():
        it = items.get(k)
        if it is None:
            sys.exit(f"{k} 不在单子 {sheet}.json 里")
        if v["choice"] not in ("defer", None) and it["opts"][v["choice"]]["label"] != v["label"]:
            sys.exit(f"{k} 的选项文字与单子不一致（发布后改过选项？）：提交里是「{v['label']}」，单子里是「{it['opts'][v['choice']]['label']}」")
os.makedirs(os.path.join(HERE, "answers"), exist_ok=True)
json.dump(out, open(os.path.join(HERE, "answers", f"{sheet}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
by_sub = {}
for k, v in sorted(out.items()):
    by_sub.setdefault(v["submission"], []).append((k.split("~", 1)[1], v))
session = opts.get("session", "one-rules")
intents = []
for number, rows in sorted(by_sub.items()):
    lines = "；".join(f"{no}「{v['label']}」" + ("（未作答）" if v.get("unanswered") else "（按推荐预选，未改动）" if v.get("defaulted") else "") + (f"（说明：{v['note']}）" if v.get("note") else "") for no, v in rows)
    quote = f"{sheet} 决策页提交 {number}：{lines}"
    note = f"答复 rules/decide/answers/{sheet}.json；按推荐 {sum(v['recommended'] for _, v in rows)} 题、改选 {sum(not v['recommended'] and v['choice'] not in ('defer', None) for _, v in rows)} 题、暂不定 {sum(v['choice'] == 'defer' for _, v in rows)} 题、未作答 {sum(v['choice'] is None for _, v in rows)} 题"
    intent_dir = os.path.join(HERE, "..", "..", "intent")
    existing = next((f for f in sorted(glob.glob(os.path.join(intent_dir, "*.md"))) if f"提交 {number}：" in open(f, encoding="utf-8").read()), None)
    if existing:
        intents.append((number, os.path.basename(existing)))
    elif not opts.get("no-intent"):
        esc = lambda t: t.replace("|", "\\|")
        r = subprocess.run([sys.executable, os.path.join(HERE, "..", "..", "new_intent.py"), f"--session={session}", *passed, esc(quote), esc(note)], capture_output=True, text=True)
        path = next((l for l in r.stdout.splitlines() if l.startswith("intent/")), "")
        if r.returncode != 0 or not path:
            sys.exit(f"生成 intent 失败（返回码 {r.returncode}）：{(r.stderr or r.stdout)[-300:]}；请检查 docs/modernization/intent/ 下有无半成品片段后再处理，不要手改用户原话")
        intents.append((number, os.path.basename(path)))
    else:
        intents.append((number, ""))
print(f"{sheet}：{len(out)} 题，提交 {len(by_sub)} 次；对账通过；答复已写 answers/{sheet}.json")
for number, name in intents:
    print(f"  提交 {number} → intent {name or '（--no-intent，未生成）'}")
if opts.get("inbox-id"):
    last = max(by_sub, key=lambda n: max(v["submitted"] for _, v in by_sub[n]))   # 按提交时刻，不按编号字符串
    name = dict(intents).get(last, "")
    at = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
    write = {"action": "update", "collection": "inbox", "doc_id": opts["inbox-id"],
             "data": {"status": "decided", "decidedAt": at, "intent": name,
                      "answer": {"text": f"在确认页提交，编号 {last}", "at": at}}}
    print("收件箱回写（用 ArtifactData 对收件箱页执行，带 if_version）：")
    print(json.dumps(write, ensure_ascii=False, indent=1))
