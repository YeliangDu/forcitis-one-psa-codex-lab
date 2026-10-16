"""合并人员组收敛分片为 people/commands.yaml、state-machines.yaml、events.yaml、raci.yaml（只读分片，覆盖输出）。
状态机统一为 converge_check 口径：states 为字符串或 {name, terminal}；initial 为字符串；创建转换 from 为 null。"""
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
OUT = HERE.parent
PARTS = ["vs07", "vs13att", "vs13pay", "vs0911", "b31"]
CREATE = {None, "", "[新建]", "（新建）", "新建", "[创建]"}


def load(name, kind):
    p = HERE / f"{name}.{kind}.yaml"
    return yaml.safe_load(p.read_text(encoding="utf-8")) if p.exists() else None


def norm_machine(m, src):
    m = dict(m)
    states, term = [], set(m.get("terminal") or [])
    for s in m.get("states") or []:
        if isinstance(s, dict):
            if s.get("terminal"):
                term.add(s["name"])
            states.append(s["name"])
        else:
            states.append(s)
    m["states"] = [{"name": s, "terminal": True} if s in term else s for s in states]
    ini = m.get("initial")
    if isinstance(ini, list):
        m["initial"] = ini[0] if ini else None
    trs = []
    for t in m.get("transitions") or []:
        t = dict(t)
        if t.get("from") in CREATE:
            t["from"] = None
        trs.append(t)
    m["transitions"] = trs
    m["source_part"] = src
    m.pop("terminal", None)
    return m


def main():
    cmds, machines, events, raci, roles = [], [], {}, [], {}
    for p in PARTS:
        c = load(p, "commands")
        if c:
            for x in c.get("commands", []):
                cmds.append(dict(x, source_part=p))
        s = load(p, "state-machines")
        if s:
            for m in s.get("machines") or s.get("state_machines") or []:
                machines.append(norm_machine(m, p))
        e = load(p, "events")
        if e:
            events[p] = e
        r = load(p, "raci")
        if r:
            raci += [dict(x, source_part=p) for x in r.get("entries", [])]
            rl = r.get("roles") or {}
            if isinstance(rl, dict):
                for k, v in rl.items():
                    roles.setdefault(k, []).append(v)
    hdr = "# 人员组收敛产物（提议稿；由 parts/merge_converge.py 从分片合并，改分片后重跑，不手改本文件）\n"
    dump = lambda o: yaml.safe_dump(o, allow_unicode=True, sort_keys=False, width=200)
    (OUT / "commands.yaml").write_text(hdr + dump({"commands": cmds}), encoding="utf-8")
    (OUT / "state-machines.yaml").write_text(hdr + dump({"machines": machines}), encoding="utf-8")
    (OUT / "events.yaml").write_text(hdr + dump({"by_part": events}), encoding="utf-8")
    (OUT / "raci.yaml").write_text(hdr + dump({"entries": raci, "roles": roles}), encoding="utf-8")
    print(f"commands {len(cmds)}, machines {len(machines)}, raci {len(raci)}")


if __name__ == "__main__":
    sys.exit(main())
