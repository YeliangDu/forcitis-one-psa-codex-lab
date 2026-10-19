"""人员组合并脚本：分片 parts/* → processes.people.yaml、scenarios.people.yaml、commands.yaml、state-machines.yaml、events.yaml、raci.yaml、NOTES.md。

用法：python3 parts/rebuild.py（在 closeout/people/ 下运行）。分片是底稿，合并产物不手改；改分片后重跑。
- 流程按 id 文本抽块合并，保留注释；PRC-83、84、137（样板）随 vs07 分片。
- 状态机统一为 converge_check 口径：states 为字符串或 {name, terminal}；initial 为字符串；创建转换 from 为 null；
  同一聚合的多台状态机合成一台（状态名加「子机:」前缀，各子机初态以 from: null 的入口转换接入）；
  分片的 non_state_commands 写成各非终态上的自环（kind: non_state）。
- 命令目录中名以「撤回 / 撤销」起头且骨架未直接引用的，标 unused_ok（由骨架 withdraw 边触发）。
- NOTES.md：固定小节按分片拼接；「B-13 长期目标」「聚合稿回补」「方法验证 / 推翻」三节取自 NOTES.keep.md（手写，保留）。
"""
import re
import sys
from pathlib import Path

import yaml

P = Path(__file__).resolve().parent
OUT = P.parent
PROC_PARTS = ["vs07", "vs13", "vs0911", "b31"]
CONV_PARTS = ["vs07", "vs13att", "vs13pay", "vs0911", "b31"]
NOTE_PARTS = [("vs07", "入转调离（VS-07、VS-13.6）"), ("vs07conv", "B-13 聚合收敛"), ("b13data", "B-13、B-14、B-27 表"), ("vs13", "VS-13 获得报酬"),
              ("vs13att", "VS-13 考勤类聚合收敛"), ("vs13pay", "VS-13 薪酬类聚合收敛"), ("b15data", "B-15 薪酬表"),
              ("vs0911", "VS-09 / VS-11"), ("b31", "B-31 行政事务")]
SECTIONS = ["接缝问题", "结构问题", "新用语", "格式问题", "待用户定"]
CREATE = {None, "", "[新建]", "（新建）", "新建", "[创建]"}
dump = lambda o: yaml.safe_dump(o, allow_unicode=True, sort_keys=False, width=200)
HDR = "# 人员组合并产物（由 parts/rebuild.py 从分片生成，不手改；改分片后重跑）\n"


def blocks(path):
    lines = path.read_text(encoding="utf-8").split("\n")
    out, sec, i = {}, None, 0
    while i < len(lines):
        m0 = re.match(r"^([a-z_]+):\s*$", lines[i])
        if m0:
            sec = m0.group(1)
            i += 1
            continue
        m = re.match(r"^(\s*)- id: (PRC-\d+[A-Z]?)\s*$", lines[i])
        if not m:
            i += 1
            continue
        ind, j = len(m.group(1)), i + 1
        while j < len(lines):
            lj = lines[j]
            if re.match(r"^[a-z_]+:", lj) or (re.match(r"^\s{%d}- " % ind, lj) and not re.match(r"^\s{%d}\s" % (ind + 1), lj)):
                break
            j += 1
        blk = lines[i:j]
        while blk and not blk[-1].strip():
            blk.pop()
        out[m.group(2)] = (sec, "\n".join(("  " + x[ind:]) if x.strip() else "" for x in blk))
        i = j
    return out


def key(x):
    m = re.match(r"PRC-(\d+)([A-Z]?)", x)
    return int(m.group(1)), m.group(2)


def processes():
    src = {}
    for p in PROC_PARTS:
        for k, v in blocks(P / f"{p}.processes.yaml").items():
            if k in src:
                raise SystemExit(f"{k} 在两个分片里")
            src[k] = v
    t = (OUT / "processes.people.yaml").read_text(encoding="utf-8")
    out = [t[: t.index("\nprocesses:")].rstrip("\n"), ""]
    for sec in ["processes", "event_policies", "single_aggregate", "read_models"]:
        out.append(f"{sec}:\n")
        out += [src[k][1] + "\n" for k in sorted((k for k, (s, _) in src.items() if s == sec), key=key)]
    txt = "\n".join(out)
    d = yaml.safe_load(txt)
    (OUT / "processes.people.yaml").write_text(txt, encoding="utf-8")
    sc = {}
    for p in PROC_PARTS:
        f = P / f"{p}.scenarios.yaml"
        if f.exists():
            sc.update(yaml.safe_load(f.read_text(encoding="utf-8")) or {})
    ids = [u["id"] for s in ["processes", "event_policies", "single_aggregate", "read_models"] for u in d.get(s) or []]
    sc = {k: sc[k] for k in sorted(sc, key=key)}
    (OUT / "scenarios.people.yaml").write_text(HDR + dump(sc), encoding="utf-8")
    return d, [i for i in ids if i not in sc]


def load(name, kind):
    f = P / f"{name}.{kind}.yaml"
    return yaml.safe_load(f.read_text(encoding="utf-8")) if f.exists() else None


def norm(m):
    states, term = [], set(m.get("terminal") or [])
    for s in m.get("states") or []:
        n = s["name"] if isinstance(s, dict) else s
        if isinstance(s, dict) and s.get("terminal"):
            term.add(n)
        states.append(n)
    ini = m.get("initial")
    ini = (ini[0] if ini else None) if isinstance(ini, list) else ini
    trs = []
    for t in m.get("transitions") or []:
        t = dict(t)
        if t.get("from") in CREATE:
            t["from"] = None
        trs.append(t)
    for ns in m.get("non_state_commands") or []:
        c = ns["command"] if isinstance(ns, dict) else ns
        for s in states:
            if s not in term:
                trs.append({"from": s, "to": s, "command": c, "kind": "non_state"})
    return states, term, ini, trs


def combine(agg, ms, part):
    if len(ms) == 1:
        states, term, ini, trs = norm(ms[0])
        m = {k: v for k, v in ms[0].items() if k not in ("states", "terminal", "initial", "transitions", "non_state_commands")}
        return dict(m, states=[{"name": s, "terminal": True} if s in term else s for s in states], initial=ini, transitions=trs, source_part=part)
    allst, alltr, subs = [], [], []
    for m in ms:
        pre = (m.get("machine") or m.get("entity") or "子机") + ":"
        states, term, ini, trs = norm(m)
        subs.append(m.get("machine"))
        allst += [{"name": pre + s, "terminal": True} if s in term else pre + s for s in states]
        q = lambda s: None if s is None else pre + s
        for t in trs:
            f = t.get("from")
            alltr.append(dict(t, **{"from": [q(x) for x in f] if isinstance(f, list) else ("*" if f == "*" else q(f)), "to": q(t.get("to"))}))
        if ini and not any(t.get("from") is None and t.get("to") == ini for t in trs):
            alltr.append({"from": None, "to": pre + ini, "command": None, "kind": "entry", "note": "子生命周期入口"})
    return {"aggregate": agg, "sub_machines": subs, "states": allst, "transitions": alltr, "source_part": part,
            "note": "同一聚合的多个子生命周期合成一台（rebuild.py）；状态名带子机前缀"}


def converge(proc):
    used = set()
    for s in ["processes", "event_policies", "single_aggregate", "read_models"]:
        for u in proc.get(s) or []:
            for st in (u.get("steps") or ([u["step"]] if u.get("step") else [])):
                if st.get("command"):
                    used.add(re.sub(r"^〔缺〕", "", st["command"]))
            for c in u.get("commands") or []:
                used.add(c.get("command") if isinstance(c, dict) else c)
    cmds, machines, events, raci, roles = [], {}, {}, [], {}
    for p in CONV_PARTS:
        c = load(p, "commands") or {}
        for x in c.get("commands", []):
            x = dict(x, source_part=p)
            n = re.sub(r"^〔缺〕", "", x["name"])
            if n not in used and re.match(r"^(撤回|撤销)", n) and not x.get("unused_ok"):
                x["unused_ok"] = "由骨架 withdraw 边触发"
            cmds.append(x)
        s = load(p, "state-machines") or {}
        for m in s.get("machines") or s.get("state_machines") or []:
            machines.setdefault(m["aggregate"], []).append((m, p))
        e = load(p, "events")
        if e:
            events[p] = e
        r = load(p, "raci") or {}
        raci += [dict(x, source_part=p) for x in r.get("entries", [])]
        rl = r.get("roles") or {}
        for k, v in (rl.items() if isinstance(rl, dict) else []):
            roles.setdefault(k, []).append(v)
    ms = [combine(a, [m for m, _ in v], v[0][1]) for a, v in machines.items()]
    (OUT / "commands.yaml").write_text(HDR + dump({"commands": cmds}), encoding="utf-8")
    (OUT / "state-machines.yaml").write_text(HDR + dump({"machines": ms}), encoding="utf-8")
    (OUT / "events.yaml").write_text(HDR + dump({"by_part": events}), encoding="utf-8")
    (OUT / "raci.yaml").write_text(HDR + dump({"entries": raci, "roles": roles}), encoding="utf-8")
    return len(cmds), len(ms), len(raci)


def secs(path):
    s = path.read_text(encoding="utf-8")
    d = {}
    for x in re.split(r"^## ", s, flags=re.M)[1:]:
        h, b = (x.split("\n", 1) + [""])[:2]
        d[h.strip()] = re.sub(r"^### ", "#### ", b.strip(), flags=re.M)
    return d


def get(d, k):
    return next((b for h, b in d.items() if h.startswith(k)), None)


def notes():
    keep = secs(OUT / "NOTES.keep.md")
    parts = {k: secs(P / f"{k}.notes.md") for k, _ in NOTE_PARTS if (P / f"{k}.notes.md").exists()}
    out = ["# 人员组 NOTES（one-arch-people）", "",
           "由 `parts/rebuild.py` 从各分片 `parts/*.notes.md` 与手写的 `NOTES.keep.md` 合并，不手改。按 `../CONVENTIONS.md` §4 固定小节，每节按分片分；编号（V-n、U-n、R-B31-n 等）为分片内部编号，合成确认页时由 one-architecture 统一。", ""]
    out += ["## B-13 长期目标（用户 P-01）", "", get(keep, "B-13 长期目标") or "", ""]
    for sec in SECTIONS:
        out += [f"## {sec}", ""]
        for k, name in NOTE_PARTS:
            b = get(parts.get(k, {}), sec)
            if b:
                out += [f"### {name}", "", b, ""]
    out += ["## 聚合稿回补（交 one-architecture 并入 `ENTERPRISE_AGGREGATES_DRAFT.md` §2.2）", "", get(keep, "聚合稿回补") or "", ""]
    extra = ["每步在哪里完成", "验收场景", "规则候选", "权责对照条目", "收敛自检"]
    for e in extra:
        blk = [(name, get(parts.get(k, {}), e)) for k, name in NOTE_PARTS]
        blk = [(n, b) for n, b in blk if b]
        if blk:
            out += [f"## 附：{e}", ""]
            for n, b in blk:
                out += [f"### {n}", "", b, ""]
    out += ["## 方法验证 / 推翻", "", get(keep, "方法验证") or "", ""]
    (OUT / "NOTES.md").write_text("\n".join(out), encoding="utf-8")


def main():
    proc, missing = processes()
    n = converge(proc)
    notes()
    cnt = {k: len(proc.get(k) or []) for k in ["processes", "event_policies", "single_aggregate", "read_models"]}
    print(f"流程 {cnt}；无场景单元 {missing}；命令 {n[0]}、状态机 {n[1]}、权责 {n[2]}")


if __name__ == "__main__":
    sys.exit(main())
