#!/usr/bin/env python3
"""从工作卡的键值头生成工作看板 WORK_BOARD.md（不入仓，同 STATUS.md），并校验键值头。

卡文件开头写键值头（`---` 包住，每行「键: 值」），其后照常写卡正文：
  ---
  卡号: TS-UNIQ
  状态: 待派 | 进行中 | 阻塞 | 完成 | 并入 | 暂缓
  负责会话: one-1a-par            （进行中、阻塞必填，且须是在用的会话）
  前置: R-1b-P5, K-3a             （卡号，逗号或顿号分隔，可空）
  并入到: R-1b                     （状态为并入时必填）
  暂缓原因: …（出处）              （状态为暂缓时必填）
  ---
扫描目录见 CARD_DIRS。没有键值头的卡不算错，只在看板「未迁移」一栏列出。
校验（--check，不通过退出 1）：键与状态合法、卡号唯一；进行中 / 阻塞有在用负责会话（sessions/<名>.md 存在）；前置存在且无环；并入目标存在；暂缓有原因。
看板另列「可开工但没人认领」：待派、无负责会话、前置全部完成或并入。
用法：build_work_board.py | build_work_board.py --check | build_work_board.py --self-test
"""
import os, re, sys

BASE = os.path.dirname(os.path.abspath(__file__))
CARD_DIRS = ("phase1a/cards", "data/repair-cards", "work/cards")
STATES = ("待派", "进行中", "阻塞", "完成", "并入", "暂缓")
KEYS = ("卡号", "状态", "负责会话", "前置", "并入到", "暂缓原因")
DONE = ("完成", "并入")
FRONT = re.compile(r"\A---[ \t]*\n(?:(.*?)\n)?---[ \t]*(?:\n|\Z)", re.S)
SOURCE = re.compile(r"intent|#\d+|ADR-\d{4}|§|SESSION_PLAYBOOK|用户|\.md")


def parse_card(path, text):
    """返回卡 dict；没有键值头返回 None；键值头不合规抛 ValueError。"""
    text = text.lstrip("\ufeff").replace("\r\n", "\n")
    m = FRONT.match(text)
    if not m:
        if text.startswith("---"):
            raise ValueError(f"{path}：以 --- 开头但键值头不完整（结尾的 --- 须独占一行）")
        return None
    fields = {}
    for line in (m.group(1) or "").split("\n"):
        k, sep, v = line.partition(":")
        k, v = k.strip(), re.sub(r"\s{2,}#\s.*$", "", v.strip())
        if not sep or k not in KEYS or k in fields:
            raise ValueError(f"{path}：键值头每行须为「键: 值」，键取自 {'、'.join(KEYS)} 且不重复：{line[:30]}")
        if v.startswith("<"):
            raise ValueError(f"{path}：「{k}」还是模板占位：{v[:20]}")
        fields[k] = v
    for k in ("卡号", "状态"):
        if not fields.get(k):
            raise ValueError(f"{path}：键值头缺少 {k}")
    title = next((l[2:].strip() for l in text[m.end():].split("\n") if l.startswith("# ")), "")
    return dict(id=fields["卡号"], state=fields["状态"], owner=fields.get("负责会话", ""), after=[x for x in re.split(r"[,，、\s]+", fields.get("前置", "")) if x],
                merged_into=fields.get("并入到", ""), reason=fields.get("暂缓原因", ""), title=title, path=path)


def validate(cards, active):
    errors, by_id = [], {}
    for c in cards:
        if c["id"] in by_id:
            errors.append(f"{c['path']}：卡号 {c['id']} 与 {by_id[c['id']]['path']} 重复")
        by_id[c["id"]] = c
    for c in cards:
        tag = f"{c['path']}（{c['id']}）"
        if c["state"] not in STATES:
            errors.append(f"{tag}：状态须为 {'、'.join(STATES)}：{c['state']}")
        if c["state"] in ("进行中", "阻塞") and not c["owner"]:
            errors.append(f"{tag}：状态 {c['state']} 须有负责会话")
        if c["owner"] and c["state"] in ("待派", "进行中", "阻塞") and c["owner"] not in active:
            errors.append(f"{tag}：负责会话 {c['owner']} 不是在用的会话（sessions/ 下没有画像）")
        for p in c["after"]:
            if p not in by_id:
                errors.append(f"{tag}：前置 {p} 不存在")
        if c["state"] == "并入" and c["merged_into"] not in by_id:
            errors.append(f"{tag}：状态并入须有存在的并入到：{c['merged_into'] or '空'}")
        if c["state"] == "暂缓" and not c["reason"]:
            errors.append(f"{tag}：状态暂缓须写暂缓原因（含出处）")
        elif c["state"] == "暂缓" and not SOURCE.search(c["reason"]):
            errors.append(f"{tag}：暂缓原因须带出处（intent、#PR、ADR-NNNN、§ 或 .md 路径）：{c['reason'][:30]}")
        if c["state"] == "并入" and c["merged_into"] == c["id"]:
            errors.append(f"{tag}：不能并入自己")
    for c in cards:   # 并入成环
        seen, cur = {c["id"]}, c
        while cur["state"] == "并入" and cur["merged_into"] in by_id and cur["merged_into"] not in seen:
            seen.add(cur["merged_into"]); cur = by_id[cur["merged_into"]]
        if cur["state"] == "并入" and cur["merged_into"] in seen and cur["merged_into"] != cur["id"]:
            errors.append(f"{c['path']}（{c['id']}）：并入成环")
    for start in by_id:   # 前置成环
        stack, seen = [start], set()
        while stack:
            cur = stack.pop()
            for p in by_id.get(cur, {}).get("after", []):
                if p == start:
                    errors.append(f"{by_id[start]['path']}（{start}）：前置成环"); stack = []; break
                if p not in seen and p in by_id:
                    seen.add(p); stack.append(p)
    return errors


def ready_unclaimed(cards):
    by_id = {c["id"]: c for c in cards}
    return [c for c in cards if c["state"] == "待派" and not c["owner"] and all(by_id.get(p, {}).get("state") in DONE for p in c["after"])]


def render(cards, headerless):
    by_state = {s: [c for c in cards if c["state"] == s] for s in STATES}
    unclaimed = {c["id"] for c in ready_unclaimed(cards)}

    def table(rows, extra):
        if not rows:
            return "无\n"
        return "| 卡号 | 标题 | 负责会话 | 前置 | " + extra + " |\n|---|---|---|---|---|\n" + "".join(
            f"| {c['id']} | {c['title'].replace('|', '／')} | {c['owner'] or '—'} | {'、'.join(c['after']) or '—'} | "
            f"{(c['reason'] or c['merged_into'] or '—').replace('|', '／')} |\n" for c in rows)

    out = ["# 工作看板\n\n由 `build_work_board.py` 从工作卡键值头生成，不手改，不入仓。卡的格式见该脚本头部注释。\n"]
    out.append(f"## 可开工但没人认领（{len(unclaimed)}）\n\n" + table([c for c in by_state["待派"] if c["id"] in unclaimed], "备注"))
    out.append(f"## 进行中（{len(by_state['进行中'])}）\n\n" + table(by_state["进行中"], "备注"))
    out.append(f"## 阻塞（{len(by_state['阻塞'])}）\n\n" + table(by_state["阻塞"], "备注"))
    out.append(f"## 待派（前置未完成或已有负责会话）（{len(by_state['待派']) - len(unclaimed)}）\n\n" + table([c for c in by_state["待派"] if c["id"] not in unclaimed], "备注"))
    out.append(f"## 暂缓（{len(by_state['暂缓'])}）\n\n" + table(by_state["暂缓"], "暂缓原因"))
    out.append(f"## 完成 {len(by_state['完成'])} 张，并入 {len(by_state['并入'])} 张\n")
    out.append(f"## 未迁移（无键值头）{len(headerless)} 张\n\n" + ("\n".join(f"- `{p}`" for p in headerless) if headerless else "无") + "\n")
    return "\n".join(out)


def active_sessions(root):
    folder = os.path.join(root, "sessions")
    return {f[:-3] for f in os.listdir(folder) if f.endswith(".md") and not f.startswith("_")} if os.path.isdir(folder) else set()


def collect(root):
    cards, headerless = [], []
    for d in CARD_DIRS:
        folder = os.path.join(root, d)
        if not os.path.isdir(folder):
            continue
        for f in sorted(os.listdir(folder)):
            if not f.endswith(".md") or f == "README.md":
                continue
            path = f"{d}/{f}"
            card = parse_card(path, open(os.path.join(folder, f), encoding="utf-8").read())
            (cards if card else headerless).append(card or path)
    return cards, headerless


def main(argv):
    if argv not in ([], ["--check"]):
        sys.exit(__doc__)
    try:
        cards, headerless = collect(BASE)
    except ValueError as e:
        sys.exit(f"工作卡键值头校验失败：{e}")
    errors = validate(cards, active_sessions(BASE))
    ready = ready_unclaimed(cards)
    if ready:
        print(f"可开工但没人认领 {len(ready)} 张（见 WORK_BOARD.md）：{'、'.join(c['id'] for c in ready[:8])}" + ("…" if len(ready) > 8 else ""), file=sys.stderr)
    if errors:
        sys.exit("工作卡键值头校验失败：\n" + "\n".join(errors))
    if argv == ["--check"]:
        print(f"check: {len(cards)} 张有键值头，{len(headerless)} 张未迁移")
        return
    open(os.path.join(BASE, "WORK_BOARD.md"), "w", encoding="utf-8").write(render(cards, headerless))
    print(f"工作看板：{len(cards)} 张有键值头，{len(headerless)} 张未迁移")


def self_test():
    def card(text, path="x.md"):
        return parse_card(path, text)

    def mk(i, state="待派", owner="", after="", merged="", reason=""):
        t = f"---\n卡号: {i}\n状态: {state}\n负责会话: {owner}\n前置: {after}\n并入到: {merged}\n暂缓原因: {reason}\n---\n# 标题{i}\n正文\n"
        return card(t, f"{i}.md")

    active = {"one-a", "one-b"}
    ok = lambda *cs: not validate(list(cs), active)
    bad = lambda frag, *cs: any(frag in e for e in validate(list(cs), active))
    assert card("---\n卡号: A\n状态: 待派     # 待派 | 进行中\n暂缓原因: 等 (#96)\n---\n")["state"] == "待派" and card("---\n卡号: A\n状态: 待派\n暂缓原因: 等 (#96)\n---\n")["reason"] == "等 (#96)"
    assert card("# 无头\n") is None and mk("A")["title"] == "标题A" and mk("B", after="A、C, D")["after"] == ["A", "C", "D"]
    for text in ("---\n状态: 待派\n---\n", "---\n卡号: A\n---\n", "---\n卡号: A\n状态: 待派\n卡号: B\n---\n", "---\n卡号: A\n状态: 待派\n别的: x\n---\n", "---\n卡号: A\n状态: 待派\n没有冒号\n---\n"):
        try:
            card(text); raise AssertionError(text)
        except ValueError:
            pass
    assert ok(mk("A", "进行中", "one-a"), mk("B", "待派", "", "A"), mk("C", "完成"), mk("D", "并入", "", "", "C"), mk("E", "暂缓", "", "", "", "等 X（intent y）"))
    assert bad("状态须为", mk("A", "做着"))
    assert bad("须有负责会话", mk("A", "进行中")) and bad("须有负责会话", mk("A", "阻塞"))
    assert bad("不是在用的会话", mk("A", "进行中", "one-gone")) and bad("不是在用的会话", mk("A", "待派", "one-gone"))
    assert bad("前置 Z 不存在", mk("A", after="Z"))
    assert bad("并入", mk("A", "并入")) and bad("并入", mk("A", "并入", "", "", "Z"))
    assert bad("暂缓原因", mk("A", "暂缓")) and bad("须带出处", mk("A", "暂缓", "", "", "", "等一等"))
    assert bad("不能并入自己", mk("A", "并入", "", "", "A")) and bad("并入成环", mk("A", "并入", "", "", "B"), mk("B", "并入", "", "", "A"))
    assert ok(mk("A", "完成", "one-gone"))   # 历史卡的负责会话退役后不报错
    for text in ("---\r\n卡号: A\r\n状态: 待派\r\n---\r\n# t\r\n", "\ufeff---\n卡号: A\n状态: 待派\n---", "---\n卡号: A\n状态: 待派\n---  \n# t\n"):
        assert card(text)["id"] == "A", text
    for text in ("---\n---\n", "---\n卡号: A\n状态: 待派\n", "---\n卡号: A\n状态: 待派\n暂缓原因: <原因（出处）> # x\n---\n"):
        try:
            card(text); raise AssertionError(text)
        except ValueError:
            pass
    assert bad("重复", mk("A"), mk("A"))
    assert bad("成环", mk("A", after="B"), mk("B", after="A")) and bad("成环", mk("A", after="A"))
    assert ok(mk("A", "完成"), mk("B", after="A"), mk("C", after="A, B"))
    ready = ready_unclaimed([mk("A", "完成"), mk("B", after="A"), mk("C", after="B"), mk("D", "待派", "one-a", "A"), mk("E")])
    assert sorted(c["id"] for c in ready) == ["B", "E"], ready
    assert "标｜题".replace("｜", "／") in render([card("---\n卡号: A\n状态: 待派\n---\n# 标|题\n")], [])
    assert {c["id"] for c in ready_unclaimed([mk("A", "并入", "", "", "B"), mk("B"), mk("C", after="A")])} == {"B", "C"}
    board = render([mk("A", "完成"), mk("B", after="A"), mk("C", "暂缓", "", "", "", "原因|竖线")], ["old.md"])
    assert "可开工但没人认领（1）" in board and "| B |" in board and "原因／竖线" in board and "old.md" in board
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "sessions"))
        for n in ("one-x.md", "_retired.md", "note.txt"):
            open(os.path.join(d, "sessions", n), "w").write("x")
        assert active_sessions(d) == {"one-x"}
    cards, headerless = collect(BASE)   # 仓库现状不得报错
    assert not validate(cards, active_sessions(BASE))
    print("build_work_board self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        main(sys.argv[1:])
