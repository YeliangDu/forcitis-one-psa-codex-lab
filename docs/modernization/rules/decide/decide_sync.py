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
不自动改规则卡、ADR；转写仍由会话执行并复核。"""
import datetime, json, os, subprocess, sys

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
os.makedirs(os.path.join(HERE, "answers"), exist_ok=True)
json.dump(out, open(os.path.join(HERE, "answers", f"{sheet}.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1, sort_keys=True)
by_sub = {}
for k, v in sorted(out.items()):
    by_sub.setdefault(v["submission"], []).append((k.split("~", 1)[1], v))
session = opts.get("session", "one-rules")
intents = []
for number, rows in sorted(by_sub.items()):
    lines = "；".join(f"{no}「{v['label']}」" + (f"（说明：{v['note']}）" if v.get("note") else "") for no, v in rows)
    quote = f"{sheet} 决策页提交 {number}：{lines}"
    note = f"答复 rules/decide/answers/{sheet}.json；按推荐 {sum(v['recommended'] for _, v in rows)} 题、改选 {sum(not v['recommended'] and v['choice'] != 'defer' for _, v in rows)} 题、暂不定 {sum(v['choice'] == 'defer' for _, v in rows)} 题"
    if not opts.get("no-intent"):
        r = subprocess.run([sys.executable, os.path.join(HERE, "..", "..", "new_intent.py"), f"--session={session}", quote, note], capture_output=True, text=True)
        path = next((l for l in r.stdout.splitlines() if l.startswith("intent/")), "")
        intents.append((number, os.path.basename(path)))
    else:
        intents.append((number, ""))
print(f"{sheet}：{len(out)} 题，提交 {len(by_sub)} 次；对账通过；答复已写 answers/{sheet}.json")
for number, name in intents:
    print(f"  提交 {number} → intent {name or '（--no-intent，未生成）'}")
if opts.get("inbox-id"):
    last = sorted(by_sub)[-1]
    name = dict(intents).get(last, "")
    at = datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")
    write = {"action": "update", "collection": "inbox", "doc_id": opts["inbox-id"],
             "data": {"status": "decided", "decidedAt": at, "intent": name,
                      "answer": {"text": f"在确认页提交，编号 {last}", "at": at}}}
    print("收件箱回写（用 ArtifactData 对收件箱页执行，带 if_version）：")
    print(json.dumps(write, ensure_ascii=False, indent=1))
