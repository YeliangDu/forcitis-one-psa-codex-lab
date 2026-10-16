#!/usr/bin/env python3
"""决定链路一条龙：读用户在确认页的提交 → 对账 → 生成 intent 片段 → 打印收件箱回写所需的 ArtifactData 批量写入。
用法：
  python3 docs/modernization/rules/decide/decide_sync.py <单子> <out_dir> [--session=one-rules] [--inbox-id=<收件箱事项 id>] [--no-intent]
前提：已用 ArtifactData list 把该确认页的 answers、answers-submit 存到 <out_dir>。
做三件事：
1. 对账：调用 export_answers 的逻辑，有提交后又改过的题则报错退出；
2. 每个提交编号生成一条 intent 片段（用户原话 = 题号 + 所选项文字 + 说明，落点指向答复文件 rules/decide/answers/<单子>.json），文件名打印出来；
3. 打印 inbox 事项改为已定所需的 ArtifactData update 内容（status、answer、decidedAt、intent 文件名），由会话用 ArtifactData 执行：
   页面是各自独立的发布件，确认页不能直接写收件箱，所以回写由会话完成，并在报告里附这条命令的输出。
不自动改规则卡、ADR；转写仍由会话执行并复核。
幂等：同一提交编号已有 intent 片段（片段正文含「提交 <编号>」）时不重复生成。原话里的「|」按 new_intent 的要求转义为「\\|」，不改动其他字符；生成失败（返回码非 0 或没有产出片段）即退出，不打印成功。
覆盖检查：存在 sheets/<单子>.json 时，核对提交是否覆盖全部题、所选序号对应的选项文字是否仍与单子一致，不一致报错退出（发布后改过选项会造成序号错位）。"""
import datetime, glob, json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import export_answers  # noqa: E402

args = [a for a in sys.argv[1:] if not a.startswith("--")]
opts = dict(a[2:].split("=", 1) if "=" in a else (a[2:], True) for a in sys.argv[1:] if a.startswith("--"))
if len(args) != 2:
    sys.exit(__doc__)
sheet, base = args
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
        if v["choice"] != "defer" and it["opts"][v["choice"]]["label"] != v["label"]:
            sys.exit(f"{k} 的选项文字与单子不一致（发布后改过选项？）：提交里是「{v['label']}」，单子里是「{it['opts'][v['choice']]['label']}」")
os.makedirs(os.path.join(HERE, "answers"), exist_ok=True)
json.dump(out, open(os.path.join(HERE, "answers", f"{sheet}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
by_sub = {}
for k, v in sorted(out.items()):
    by_sub.setdefault(v["submission"], []).append((k.split("~", 1)[1], v))
session = opts.get("session", "one-rules")
intents = []
for number, rows in sorted(by_sub.items()):
    lines = "；".join(f"{no}「{v['label']}」" + ("（按推荐预选，未改动）" if v.get("defaulted") else "") + (f"（说明：{v['note']}）" if v.get("note") else "") for no, v in rows)
    quote = f"{sheet} 决策页提交 {number}：{lines}"
    note = f"答复 rules/decide/answers/{sheet}.json；按推荐 {sum(v['recommended'] for _, v in rows)} 题、改选 {sum(not v['recommended'] and v['choice'] != 'defer' for _, v in rows)} 题、暂不定 {sum(v['choice'] == 'defer' for _, v in rows)} 题"
    intent_dir = os.path.join(HERE, "..", "..", "intent")
    existing = next((f for f in sorted(glob.glob(os.path.join(intent_dir, "*.md"))) if f"提交 {number}：" in open(f, encoding="utf-8").read()), None)
    if existing:
        intents.append((number, os.path.basename(existing)))
    elif not opts.get("no-intent"):
        esc = lambda t: t.replace("|", "\\|")
        r = subprocess.run([sys.executable, os.path.join(HERE, "..", "..", "new_intent.py"), f"--session={session}", esc(quote), esc(note)], capture_output=True, text=True)
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
