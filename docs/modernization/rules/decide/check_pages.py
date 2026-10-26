#!/usr/bin/env python3
"""确认页出题检查：对 architecture/decision-page/*.json 与 rules/decide/sheets/*.json 的每道题检查，不过不得发布（用户 2026-10-16 批准）。

题的格式（同 rules/decide/README.md 的 sheets 格式）：no、title、ctx、src、opts[{label, effect, rec, sug}]、basis[]、practice[]、now、check。
题型用可选字段 kind 标记：choice（默认，一般选择题）| approve（批准题：批准/修改/不批准）| selection（选型题：不预选）| fact（事实题：不预选）| param（参数行）。
检查（编号对应用户的七条）：
  R1 一般选择题必须有推荐项，且推荐项不是「暂不定」；选型题、事实题例外（须标 kind）
  R2 依据栏（basis）不得为空，不得只有「来源建议 / 原稿 / 草稿」之类自引；一般选择题与参数行适用，批准题、选型题、事实题例外
  R3 题里引用的 intent 文件名在 origin/main 的 docs/modernization/intent/ 里必须存在（完整文件名须存在；只写 `2026-10-15-234418` 这种编号时，须有以它开头的文件）
  R4 题面与选项（title、opts.label）里不得出现词表登记的方言（terms.yaml 的 forbidden 与 type: dialect 别名）；用「」括起的用语、本题 terms 列表里声明的用语，
     都须在词表里（terms.yaml、backend/glossary 与 proposed、bcs.yaml 的 zh 与 words、frontend/scripts/copy-allow-list.yaml，或本题 terms_ok 里登记的提议术语）。
     其余业务用语不能靠切词判定（中文说明文对词表逐字覆盖会有上千处误报）：以「部门 / 负责人 / 总监 / 系数 / 声明 / 基线…」结尾、又切不成词表词的候选只给警告（不失败），审题时看一眼
  R5 事实题不得有预选（任何选项 rec 为真）；选型题同（intent 2026-10-15 ADR-0041 决定 6：选型不预选）
  R6 参数行（kind: param，或选项全是「数字+单位」）须在 ctx / basis / practice / effect 任一处标「经验值，配置可调」，或有出处（src 非空，或 basis 里有 intent / ADR / 标准 / 法规）
  R7 推荐理由（basis 与推荐项的 effect）不得以「老系统 / 老流程 / 与 A8 一致」为主要理由：出现这些词而没有任何权威依据（intent、ADR、规则号、标准、法规、行业做法）即不过
范围：已答的题不再检查（已答页是历史记录，题面不改、不补 terms_ok；用户 2026-10-22 经 one-coordinator）。已答＝本目录 answers/<页>.json 里有 <页>~<题号>，或页名登记在本目录 answered_pages.txt（收件箱已决但没有导出的页）。输出末行写明跳过的已答题数与整页数；本次改动涉及的页里，新增或未答的题照常检查。
用法：
  check_pages.py                      检查全部确认页（报告全部问题；有问题退出 1）
  check_pages.py <文件.json>…         只检查给定文件
  check_pages.py --changed <基线>     只检查相对基线改动或新增的确认页（scripts/ci/light.sh 用；改了哪张页就要求哪张页过）
  check_pages.py --self-test
R4 的「业务用语不在词表」只检查题面与选项标签（标题、选项文字），不查正文说明；检查方式同界面文字检查：按词表最长匹配切词，剩余的中文片段即未登记用语。
"""
import glob
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.normpath(os.path.join(HERE, "..", ".."))
ROOT = os.path.normpath(os.path.join(MOD, "..", ".."))
ANSWERS_DIR = os.path.join(HERE, "answers")
ANSWERED_REGISTRY = os.path.join(HERE, "answered_pages.txt")   # 收件箱已决但没有 answers 导出的页：一行一个页名，# 后写出处
PAGE_GLOBS = ("docs/modernization/architecture/decision-page/*.json", "docs/modernization/rules/decide/sheets/*.json")   # 旧确认页题源仍留原处（只读的历史页），照查
KINDS = ("choice", "approve", "selection", "fact", "param")
UNDECIDED = re.compile(r"暂不定|待定|先不选|不预设")
SELF_CITE = re.compile(r"来源建议|原稿|草稿|提议稿|按来源|同上|见上|无$")
ANCHOR = re.compile(r"intent|ADR-\d|RULE-\d|DATA-RULE|《[^》]+》|GB/?T?\s?\d|CAS\s?\d|PMBOK|BIZBOK|TOGAF|FIDIC|AACE|企业会计准则|暂行规定|民法典|劳动法|增值税|通行做法|行业通行|生产取证|生产数据|第\s?\d+\s?条|\d{4}-\d{2}-\d{2}-\d{3,}")
LEGACY = re.compile(r"老系统|老流程|(与|和|同)\s?A8\s?(保持)?(一致|相同)?|按老|沿用老|老做法")
INTENT_NAME = re.compile(r"\d{4}-\d{2}-\d{2}-\d{3,6}(?:-[^\s，。；、`'\"」,;]*?)?\.md")
INTENT_ID = re.compile(r"(?<![\d-])(\d{4}-\d{2}-\d{2}-\d{3,6})(?![\d])")
PARAM_LABEL = re.compile(r"^\s*([≤≥<>约]?\s*\d+(\.\d+)?\s*(天|日|小时|分钟|周|个月|月|年|%|％|元|万元|次|个|人|条|页|级|MB|GB|秒)\S{0,6}|两周|每月\s*\d+\s*日|0\.\d+)\s*$")
EMPIRICAL = re.compile(r"经验值[,，、]\s*配置可调")
PAGE_WORDS = {"暂不定", "我先不选", "批准", "修改后再看", "不批准", "请财务核实", "请问财务", "其他", "以上都不是"}   # 确认页自带的选项用语，不是业务用语
CHINESE = re.compile(r"[一-鿿]+")
FIELDS = ("ctx", "src", "now", "check")


def texts(item):
    """题里所有文字（含每个选项的 label、effect，basis、practice）。"""
    parts = [str(item.get(k, "")) for k in FIELDS + ("title",)]
    parts += [str(x) for x in item.get("basis", []) or []] + [str(x) for x in item.get("practice", []) or []]
    for o in item.get("opts", []) or []:
        parts += [str(o.get("label", "")), str(o.get("effect", ""))]
    return "\n".join(parts)


def kind_of(item):
    return item.get("kind", "choice")


def is_param(item):
    opts = item.get("opts", []) or []
    return kind_of(item) == "param" or (bool(opts) and all(PARAM_LABEL.match(str(o.get("label", ""))) for o in opts if not UNDECIDED.search(str(o.get("label", "")))) and
                                          any(PARAM_LABEL.match(str(o.get("label", ""))) for o in opts))


QUOTED = re.compile(r"[「『“]([一-鿿]{2,12})[」』”]")
SUFFIXED = re.compile(r"[一-鿿]{0,4}(?:部门|负责人|总监|系数|声明|基线|价目|产值|经营层|额度|状态机|底稿)")


def check_item(item, intent_names, vocab=None):
    kind = kind_of(item)
    if kind not in KINDS:
        return [f"{item.get('no', '?')}: kind 须取自 {'、'.join(KINDS)}：{kind}"]
    return check_item_main(item, intent_names, vocab) + check_item_rest(item, intent_names, vocab)


def check_item_main(item, intent_names, vocab=None):
    no = item.get("no", "?")
    errors = []
    kind = kind_of(item)
    opts = item.get("opts", []) or []
    recs = [o for o in opts if o.get("rec")]
    if kind not in KINDS:
        return [f"{no}: kind 须取自 {'、'.join(KINDS)}：{kind}"]
    # R5 / R1
    if kind in ("fact", "selection") and recs:
        errors.append(f"{no}: R5 {'事实题' if kind == 'fact' else '选型题'}不得预选，但选项「{recs[0].get('label', '')[:20]}」带了推荐")
    if kind in ("choice", "param", "approve"):
        if len(opts) < 2:
            errors.append(f"{no}: R1 须至少两个选项")
        if not recs:
            errors.append(f"{no}: R1 没有推荐项（选型题、事实题须标 kind: selection / fact）")
        elif all(UNDECIDED.search(str(o.get("label", ""))) for o in recs):
            errors.append(f"{no}: R1 推荐项是「暂不定」（选型题、事实题须标 kind: selection / fact）")
    # R2
    basis = [str(b) for b in item.get("basis", []) or []]
    if kind in ("choice", "param"):
        if not any(b.strip() for b in basis):
            errors.append(f"{no}: R2 依据栏为空")
        elif all(SELF_CITE.search(b) and not ANCHOR.search(b) for b in basis if b.strip()):
            errors.append(f"{no}: R2 依据只有「来源建议 / 原稿」之类自引：{basis[0][:30]}")
    # R3
    body = texts(item)
    for name in sorted(set(INTENT_NAME.findall(body)) | set(m.group(0) for m in INTENT_NAME.finditer(body))):
        if name not in intent_names:
            errors.append(f"{no}: R3 引用的 intent 文件 {name} 在 origin/main 不存在")
    named = set(INTENT_NAME.findall(body)) | {m.group(0) for m in INTENT_NAME.finditer(body)}
    for ident in sorted(set(INTENT_ID.findall(body))):
        if any(ident in n for n in named):
            continue   # 已作为完整文件名的一部分核对过
        if not any(n.startswith(ident) for n in intent_names):
            errors.append(f"{no}: R3 引用的 intent 编号 {ident} 在 origin/main 没有对应文件")
    # R4
    if vocab:
        shown = [str(item.get("title", ""))] + [str(o.get("label", "")) for o in opts]
        okset = set(item.get("terms_ok", []) or []) | PAGE_WORDS
        for text in shown:
            formal = sorted((w for w in vocab.get("formal", set()) if len(w) > 1), key=len, reverse=True)
            for phrase in vocab["forbidden"]:
                masked = text
                for w in formal:
                    if phrase in w and w != phrase:
                        masked = masked.replace(w, "")   # 正式词里含方言字串（贡献毛利 / 毛利）不算方言
                if phrase in masked:
                    errors.append(f"{no}: R4 用了词表登记的方言「{phrase}」（应写「{vocab['forbidden'][phrase]}」）")
        declared = [str(t) for t in item.get("terms", []) or []] + [m for text in shown for m in QUOTED.findall(text)]
        for term in sorted(set(declared)):
            if term not in okset and unregistered(term, vocab, okset):
                errors.append(f"{no}: R4 用语「{term}」不在词表（登记提议术语，或写进本题 terms_ok 并附登记）")
    return errors


def term_candidates(item, vocab):
    """警告用：以业务名词后缀结尾又切不成词表词的候选（不判失败）。"""
    if not vocab:
        return []
    okset = set(item.get("terms_ok", []) or []) | PAGE_WORDS
    found = set()
    for text in [str(item.get("title", ""))] + [str(o.get("label", "")) for o in item.get("opts", []) or []]:
        for m in SUFFIXED.finditer(text):
            cand = m.group(0)
            if cand not in okset and unregistered(cand, vocab, okset):
                found.add(cand)
    return sorted(found)


def check_item_rest(item, intent_names, vocab=None):
    no = item.get("no", "?")
    errors = []
    kind = kind_of(item)
    opts = item.get("opts", []) or []
    recs = [o for o in opts if o.get("rec")]
    basis = [str(b) for b in item.get("basis", []) or []]
    body = texts(item)
    # R6
    if is_param(item):
        everything = body
        has_empirical = bool(EMPIRICAL.search(everything))
        has_source = bool(str(item.get("src", "")).strip()) or any(ANCHOR.search(b) for b in basis) or any(ANCHOR.search(str(p)) for p in item.get("practice", []) or [])
        if not (has_empirical or has_source):
            errors.append(f"{no}: R6 参数行既没标「经验值，配置可调」也没有出处")
    # R7
    if kind in ("choice", "param") and recs:
        reason = "\n".join(basis + [str(recs[0].get("effect", ""))])
        if LEGACY.search(reason) and not ANCHOR.search(reason):
            errors.append(f"{no}: R7 推荐理由只有「老系统 / 老流程 / 与 A8 一致」，没有权威依据（intent、ADR、规则号、标准、法规、行业做法）")
    return errors


def segmentable(chunk, words, connectors, longest):
    """chunk 能否切成词表词；单字连接词只能夹在两个词之间（不在首尾、不相邻）。"""
    n = len(chunk)
    after_word = [False] * (n + 1)
    after_connector = [False] * (n + 1)
    for i in range(n):
        if i and not (after_word[i] or after_connector[i]):
            continue
        for size in range(1, min(longest, n - i) + 1):
            w = chunk[i:i + size]
            if w in words and (size > 1 or w not in connectors):
                after_word[i + size] = True
        if i and after_word[i] and chunk[i] in connectors:
            after_connector[i + 1] = True
    return after_word[n]


def unregistered(text, vocab, okset):
    """每个中文片段按词表最长匹配切词，切不完的片段（连同未登记的残余）即未登记用语。"""
    words = vocab["words"] | okset
    connectors, longest = vocab["connectors"], max(vocab["longest"], max([1] + [len(w) for w in okset]))
    left = []
    for chunk in CHINESE.findall(text):
        if segmentable(chunk, words, connectors, longest):
            continue
        rest, i = "", 0
        while i < len(chunk):
            for size in range(min(longest, len(chunk) - i), 0, -1):
                if chunk[i:i + size] in words and not (size == 1 and chunk[i] in connectors):
                    i += size
                    rest += " "
                    break
            else:
                rest += chunk[i]
                i += 1
        left += [r for r in rest.split() if r] or [chunk]
    return sorted(set(left))


def load_vocab(root=ROOT):
    try:
        import yaml
    except ImportError:
        return None
    def load(path):
        full = os.path.join(root, path)
        return (yaml.safe_load(open(full, encoding="utf-8")) or {}) if os.path.exists(full) else {}
    words, forbidden, terms = set(), {}, []
    for path in ("docs/modernization/glossary/terms.yaml", "backend/glossary/terms.yaml"):
        terms += load(path).get("terms", []) or []
    for f in sorted(glob.glob(os.path.join(root, "backend/glossary/proposed/*.yaml"))):
        terms += (yaml.safe_load(open(f, encoding="utf-8")) or {}).get("terms", []) or []
    for t in terms:
        zh = t.get("zh")
        if isinstance(zh, str):
            words.update(CHINESE.findall(zh))
        for a in t.get("attributes", []) or []:
            if isinstance(a, dict) and isinstance(a.get("zh"), str):
                words.update(CHINESE.findall(a["zh"]))
    for t in terms:
        phrases = list(t.get("forbidden", []) or []) + [a.get("name") for a in t.get("aliases", []) or [] if isinstance(a, dict) and a.get("type") == "dialect"]
        for p in phrases:
            if isinstance(p, str) and CHINESE.search(p) and p not in words and len(p) >= 2:
                forbidden[p] = t.get("zh", "")
    for c in load("docs/modernization/glossary/bcs.yaml").get("bcs", []) or []:
        if isinstance(c.get("zh"), str):
            words.update(CHINESE.findall(c["zh"]))
        for w in c.get("words", []) or []:
            if isinstance(w, str):
                words.add(w)
    formal = set(words)   # 只含词表正式词（terms.yaml、backend/glossary、bcs.yaml）；R4 方言遮盖只用它，不含文案允许表与作者自填的 terms_ok
    allow = load("frontend/scripts/copy-allow-list.yaml")
    connectors = {w for w in allow.get("words", []) or [] if isinstance(w, str) and len(w) == 1}
    for w in allow.get("words", []) or []:
        if isinstance(w, str) and len(w) > 1:
            words.add(w)
    return dict(words=words | connectors, formal=formal, connectors=connectors, forbidden=forbidden, longest=max([1] + [len(w) for w in words]))


def intent_files(base="origin/main"):
    r = subprocess.run(["git", "-c", "core.quotepath=off", "ls-tree", "--name-only", base, "docs/modernization/intent/"], cwd=ROOT, capture_output=True, text=True)
    names = {os.path.basename(x) for x in r.stdout.split("\n") if x}
    if names:
        return names
    folder = os.path.join(MOD, "intent")
    return set(os.listdir(folder)) if os.path.isdir(folder) else set()


def answered_nos(path, answers_dir=None, registry=None):
    """用户已答完的题号（历史记录，题面不再检查）：answers/<页>.json 里有 <页>~<题号> 的题，或页名登记在 answered_pages.txt 的整页。返回 (题号集合, 是否整页)。"""
    stem = os.path.splitext(os.path.basename(path))[0]
    registry_file = ANSWERED_REGISTRY if registry is None else registry
    if registry_file and os.path.exists(registry_file):
        for line in open(registry_file, encoding="utf-8-sig"):
            if line.split("#")[0].strip() == stem:
                return set(), True
    folder = ANSWERS_DIR if answers_dir is None else answers_dir
    answer_file = os.path.join(folder, stem + ".json")
    if os.path.exists(answer_file):
        try:
            keys = json.load(open(answer_file, encoding="utf-8"))
        except ValueError as e:
            print(f"警告 {stem}: answers 导出不是合法 JSON，按未答检查：{e}", file=sys.stderr)
            return set(), False
        if not isinstance(keys, dict):
            print(f"警告 {stem}: answers 导出不是对象，按未答检查", file=sys.stderr)
            return set(), False
        return {k.split("~", 1)[1] for k in keys if isinstance(k, str) and k.startswith(stem + "~")}, False
    return set(), False


def canon(item):
    return json.dumps(item, ensure_ascii=False, sort_keys=True)


def base_items(path, base, root=ROOT):
    """基线版本里各题的规范化内容（题号 → 文本）。没有基线（全量模式）返回 None，已答题按登记整体跳过；
    有基线而基线里没有这个文件（新页）、或读不出来时返回 {}：什么都对不上，已答登记也不算，照常检查。"""
    if not base:
        return None
    rel = os.path.relpath(path, root)
    r = subprocess.run(["git", "-c", "core.quotepath=off", "show", f"{base}:{rel}"], cwd=root, capture_output=True, text=True)
    if r.returncode:
        return {}
    try:
        data = json.loads(r.stdout)
    except ValueError:
        return {}
    items = data.get("items") if isinstance(data, dict) else data
    return {str(i.get("no")): canon(i) for i in items if isinstance(i, dict)} if isinstance(items, list) else {}


def check_file(path, names, vocab, skipped=None, answers_dir=None, registry=None, base=None):
    """skipped：传入字典时记下跳过的已答题数。base：基线版本里各题的规范化内容（见 base_items）；已答题只有在题面相对基线没变时才跳过，改过的已答题照常检查。"""
    try:
        data = json.load(open(path, encoding="utf-8"))
    except ValueError as e:
        return [f"{os.path.basename(path)}: 不是合法 JSON：{e}"]
    items = data.get("items") if isinstance(data, dict) else data
    if not isinstance(items, list):
        return [f"{os.path.basename(path)}: 没有 items 列表"]
    page_vocab = None if isinstance(data, dict) and data.get("terms_review") else vocab   # 用语审定页本来就在问未登记的词：整页不查 R4
    done, whole = answered_nos(path, answers_dir, registry)
    out = []
    count = 0
    for item in items:
        no = str(item.get("no"))
        if whole or no in done:
            if base is None or (no in base and base[no] == canon(item)):
                count += 1
                continue
        seen = []
        for e in check_item(item, names, page_vocab):
            if e not in seen:
                seen.append(e)
        out += [f"{os.path.basename(path)} {e}" for e in seen]
    if skipped is not None:
        skipped["questions"] = skipped.get("questions", 0) + count
        if items and count == len(items):
            skipped["pages"] = skipped.get("pages", 0) + 1
    return out


def changed_pages(base, root=ROOT):
    """相对基线改动、新增、重命名或尚未跟踪的确认页；基线取不到即失败（不静默放过）。"""
    def git(*args):
        return subprocess.run(["git", "-c", "core.quotepath=off", *args], cwd=root, capture_output=True, text=True)
    r = git("diff", "--name-only", "--diff-filter=AMR", f"{base}...HEAD")
    if r.returncode:
        sys.exit(f"check_pages: 取不到基线 {base}：{r.stderr.strip()[:200]}")
    paths = set(r.stdout.split()) | set(git("diff", "--name-only", "--diff-filter=AMR", "HEAD").stdout.split()) | set(git("ls-files", "--others", "--exclude-standard").stdout.split())
    return sorted(os.path.join(root, p) for p in paths if any(re.fullmatch(g.replace("*", "[^/]+"), p) for g in PAGE_GLOBS))


def main(argv):
    names = intent_files()
    vocab = load_vocab()
    if vocab is None:
        print("警告：缺少 PyYAML，R4（用语）未检查", file=sys.stderr)
    base_ref = None
    if argv[:1] == ["--changed"] and len(argv) == 2:
        base_ref = argv[1]
        files = changed_pages(argv[1])
        if not files:
            print("check_pages: 没有改动的确认页")
            return 0
    elif argv and not argv[0].startswith("--"):
        files = [os.path.abspath(a) for a in argv]
    elif not argv:
        files = sorted(f for g in PAGE_GLOBS for f in glob.glob(os.path.join(ROOT, g)))
    else:
        sys.exit(__doc__)
    skipped = {}
    problems = [p for f in files for p in check_file(f, names, vocab, skipped, base=base_items(f, base_ref))]
    for p in problems:
        print(p)
    for f in files:
        try:
            data = json.load(open(f, encoding="utf-8"))
            done, whole = answered_nos(f)
            prior = base_items(f, base_ref)
            for item in (data.get("items", []) if isinstance(data, dict) else data):
                if isinstance(data, dict) and data.get("terms_review"):
                    break
                no = str(item.get("no"))
                if (whole or no in done) and (prior is None or (no in prior and prior[no] == canon(item))):
                    continue
                for cand in term_candidates(item, vocab):
                    print(f"警告 {os.path.basename(f)} {item.get('no', '?')}: 「{cand}」像业务用语但不在词表（审题时看一眼；确认后登记提议术语或写进 terms_ok）", file=sys.stderr)
        except (ValueError, AttributeError, TypeError):
            pass
    note = f"；已答题 {skipped.get('questions', 0)} 道不再检查（其中整页已答 {skipped.get('pages', 0)} 张，依据 rules/decide/answers 导出与 answered_pages.txt）" if skipped.get("questions") else ""
    print(f"check_pages: {len(files)} 张页，{len(problems)} 处问题{note}")
    return 1 if problems else 0


def self_test():
    names = {"2026-10-15-234418-one-architecture-x.md", "2026-10-16-124649-one-architecture-我认可的规划要进SSOT.md"}
    vocab = dict(words={"合同", "收入", "确认", "条件", "审批", "的", "和", "项目", "经理", "负责", "人"}, connectors={"的", "和"}, forbidden={"老叫法": "正式用语"}, longest=4)

    def item(**kw):
        base = dict(no="T-1", title="合同收入确认条件", ctx="", src="intent 2026-10-15-234418", basis=["企业会计准则 14 号：满足条件才确认（intent 2026-10-16-124649）"],
                    practice=[], opts=[dict(label="项目经理审批", effect="按合同审批", rec=True), dict(label="暂不定", effect="", rec=False)])
        base.update(kw)
        return base

    def errs(it, v=None):
        return check_item(it, names, v)

    def has(it, code, v=None):
        return any(f": {code} " in e for e in errs(it, v))

    assert errs(item()) == [], errs(item())
    # R1
    assert has(item(opts=[dict(label="A", effect="", rec=False), dict(label="B", effect="", rec=False)]), "R1")
    assert has(item(opts=[dict(label="暂不定（我先不选）", effect="", rec=True), dict(label="B", effect="", rec=False)]), "R1")
    assert has(item(opts=[dict(label="项目经理审批", effect="", rec=True)]), "R1")
    assert not has(item(kind="selection", opts=[dict(label="A", effect="", rec=False)]), "R1") and not has(item(kind="fact", opts=[dict(label="A", effect="", rec=False)]), "R1")
    assert has(item(kind="bogus"), "kind") or errs(item(kind="bogus"))
    # R5
    assert has(item(kind="fact"), "R5") and has(item(kind="selection"), "R5")
    assert not has(item(kind="fact", opts=[dict(label="请财务核实", effect="", rec=False)]), "R5")
    # R2
    assert has(item(basis=[]), "R2") and has(item(basis=["", "  "]), "R2")
    assert has(item(basis=["来源建议", "原稿如此"]), "R2")
    assert not has(item(basis=["来源建议，且企业会计准则 14 号"]), "R2") and not has(item(kind="approve", basis=[]), "R2")
    # R3
    assert has(item(ctx="见 intent 2026-10-15-999999-no-such.md"), "R3") and has(item(ctx="见 intent 2026-10-15-999999"), "R3")
    assert not has(item(ctx="见 2026-10-15-234418-one-architecture-x.md 与 2026-10-16-124649"), "R3")
    # R4
    v = vocab
    assert has(item(title="合同老叫法"), "R4", v) and has(item(opts=[dict(label="按老叫法办", effect="", rec=True)]), "R4", v)
    assert has(item(title="合同「承担部门」审批"), "R4", v) and has(item(terms=["承担部门"]), "R4", v)
    assert not has(item(title="合同「收入」审批"), "R4", v) and not has(item(), "R4", v)
    vm = dict(v, words=v["words"] | {"贡献老叫法"}, formal={"贡献老叫法"})
    assert not has(item(opts=[dict(label="在「贡献老叫法」里扣", effect="", rec=True)]), "R4", vm)   # 正式词里含方言字串不报
    assert has(item(opts=[dict(label="在「贡献老叫法」和老叫法里扣", effect="", rec=True)]), "R4", vm)   # 单独出现仍报
    assert has(item(title="按老叫法口径算", terms_ok=["按老叫法口径算"]), "R4", vm)   # 作者自填 terms_ok 的含方言长串不能遮盖方言
    assert has(item(title="按老叫法口径算"), "R4", dict(vm, words=vm["words"] | {"按老叫法口径算"}))   # 文案允许表等非正式词也不遮盖
    assert not has(item(title="合同「承担部门」审批", terms_ok=["承担部门"]), "R4", v)
    assert not has(item(opts=[dict(label="承担部门审批", effect="", rec=True)]), "R4", v)   # 未加括号的候选只警告，不失败
    # 已答题是历史记录：含新方言也不报；本次新改的题照常报（用户 2026-10-22 经调度）
    import tempfile
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "PG.json")
        answers = os.path.join(tmp, "answers")
        os.makedirs(answers)
        bad = dict(item(title="合同老叫法", no="Q-OLD"))
        fresh = dict(item(title="合同老叫法", no="Q-NEW"))
        json.dump({"items": [bad, fresh]}, open(page, "w", encoding="utf-8"), ensure_ascii=False)
        json.dump({"PG~Q-OLD": {"choice": 0}}, open(os.path.join(answers, "PG.json"), "w", encoding="utf-8"))
        skipped = {}
        out = check_file(page, names, v, skipped, answers_dir=answers, registry="")
        assert any("Q-NEW" in e and "R4" in e for e in out) and not any("Q-OLD" in e for e in out), out
        assert skipped == {"questions": 1}, skipped
        registry = os.path.join(tmp, "answered_pages.txt")
        open(registry, "w", encoding="utf-8").write("PG  # 收件箱已决\n")
        skipped = {}
        assert check_file(page, names, v, skipped, answers_dir=answers, registry=registry) == [] and skipped == {"questions": 2, "pages": 1}, skipped
        # 已答题的题面相对基线改过，就不再是历史记录：照常检查
        edited = dict(bad, title="合同老叫法改")
        base = {"Q-OLD": canon(dict(bad, title="合同收入确认条件")), "Q-NEW": canon(fresh)}
        json.dump({"items": [edited, fresh]}, open(page, "w", encoding="utf-8"), ensure_ascii=False)
        out = check_file(page, names, v, {}, answers_dir=answers, registry="", base=base)
        assert any("Q-OLD" in e and "R4" in e for e in out), out
        unchanged = {"Q-OLD": canon(edited), "Q-NEW": canon(fresh)}
        assert not any("Q-OLD" in e for e in check_file(page, names, v, {}, answers_dir=answers, registry="", base=unchanged))
        # 新页（基线里没有）即使登记在 answered_pages 或 answers 里，也照常检查
        open(registry, "w", encoding="utf-8").write("PG\n")
        out = check_file(page, names, v, {}, answers_dir=answers, registry=registry, base={})
        assert any("Q-OLD" in e and "R4" in e for e in out), out
        open(registry, "w", encoding="utf-8").write("")
        # answers 导出损坏、不是对象：按未答检查，不崩
        open(os.path.join(answers, "PG.json"), "w", encoding="utf-8").write("3")
        assert any("Q-OLD" in e for e in check_file(page, names, v, {}, answers_dir=answers, registry=""))
        open(os.path.join(answers, "PG.json"), "w", encoding="utf-8").write("{bad")
        assert any("Q-OLD" in e for e in check_file(page, names, v, {}, answers_dir=answers, registry=""))
        # 登记文件：注释、空行、前后空白
        open(registry, "w", encoding="utf-8").write("# 注释\n\n  PG  # 出处\n#PG2\n")
        assert answered_nos(page, answers, registry) == (set(), True) and answered_nos(os.path.join(tmp, "PG2.json"), answers, registry) == (set(), False)
    assert term_candidates(item(opts=[dict(label="承担部门审批", effect="", rec=True)]), v) == ["承担部门"]
    assert term_candidates(item(opts=[dict(label="承担部门审批", effect="", rec=True)], terms_ok=["承担部门"]), v) == [] and term_candidates(item(), v) == []
    # R6
    assert has(item(src="", basis=["来源建议"], opts=[dict(label="5 天", effect="", rec=True), dict(label="10 天", effect="", rec=False)]), "R6")
    assert not has(item(src="", ctx="经验值，配置可调", opts=[dict(label="5 天", effect="", rec=True), dict(label="10 天", effect="", rec=False)]), "R6")
    assert not has(item(opts=[dict(label="5 天", effect="", rec=True), dict(label="10 天", effect="", rec=False)]), "R6")   # src 有出处
    assert has(item(kind="param", src="", basis=["老系统是 5 天"], opts=[dict(label="随便", effect="", rec=True)]), "R6")
    # R7
    assert has(item(basis=["老系统就是这样", "与 A8 一致"]), "R7") and has(item(basis=["按老流程"], src=""), "R7")
    assert not has(item(basis=["与 A8 一致；且 ADR-0044 决定 3"]), "R7")
    assert not has(item(basis=["行业通行做法：先批后付"]), "R7")
    # 反例（取自 2026-10-16 收入线审题报告）
    storage = item(title="存储选型", kind="selection", opts=[dict(label="对象存储", effect="", rec=True), dict(label="暂不定（我先不选）", effect="", rec=False)])   # VS04-01：选型题不预选
    assert has(storage, "R5")
    finance = item(title="财务审计法务题谁定", opts=[dict(label="转问财务负责人", effect="", rec=False), dict(label="暂不定（我先不选）", effect="", rec=True)])   # VS04-02：预选暂不定
    assert has(finance, "R1")
    redinvoice = item(basis=["按老流程"], src="", opts=[dict(label="红字确认", effect="照老流程", rec=True)])   # VS04-15：推荐理由按老流程
    assert has(redinvoice, "R7")
    reminder = item(no="VS04-13", kind="param", src="", basis=["来源建议"], ctx="", opts=[dict(label="28 天", effect="", rec=True), dict(label="14 天", effect="", rec=False)])   # 28 天无出处
    assert has(reminder, "R2") and has(reminder, "R6")
    stale = item(no="VS04-03", ctx="期满日已定（intent 2026-10-15-234418-gone.md）")   # 引用的 intent 文件不存在
    assert has(stale, "R3")
    # opus 首轮：裸字不算依据、更多老系统写法、参数行写法、全角括号文件名
    assert has(item(basis=["来源建议：按标准做"]), "R2") and has(item(basis=["原稿；需符合法规"]), "R2")
    assert has(item(basis=["老系统是 5 天；符合行业标准"], src=""), "R7") and has(item(basis=["和 A8 保持一致"], src=""), "R7") and has(item(basis=["同 A8"], src=""), "R7")
    assert not has(item(basis=["老系统生产取证：近 90 天 97% 都是 5 天"], src=""), "R7")
    for spelled in ("经验值,配置可调", "经验值、配置可调", "经验值，配置可调"):
        assert not has(item(src="", ctx=spelled, basis=["来源建议"], opts=[dict(label="5 天", effect="", rec=True), dict(label="10 天", effect="", rec=False)]), "R6"), spelled
    assert has(item(src="", basis=["来源建议"], opts=[dict(label="两周", effect="", rec=True), dict(label="每月 25 日", effect="", rec=False)]), "R6")
    paren = {"2026-10-16-124649-one-architecture-x（y）.md"}
    assert check_item(item(src="", ctx="见 2026-10-16-124649-one-architecture-x（y）.md"), paren, None) == [] and check_item(item(src="", ctx="见 2026-10-16-124649-one-architecture-x（z）.md"), paren, None)
    real_names = intent_files()
    if real_names:
        assert any(not n.isascii() for n in real_names) and not any(n.startswith('"') or "\\3" in n for n in real_names), "中文文件名不得被 git 转义成八进制串"
    # --changed：已跟踪改动、未跟踪的新页都查；基线取不到即失败
    import tempfile as _t
    with _t.TemporaryDirectory() as d:
        env = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@t", GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@t", GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null")
        sh = lambda *a: subprocess.run(a, cwd=d, capture_output=True, text=True, env=env, check=True)
        sh("git", "init", "-q", "-b", "main"); os.makedirs(os.path.join(d, "docs/modernization/architecture/decision-page")); os.makedirs(os.path.join(d, "docs/modernization/rules/decide/sheets"))
        pages = "docs/modernization/architecture/decision-page"
        for n in ("A.json", "B.json"):
            open(os.path.join(d, pages, n), "w").write("{}")
        open(os.path.join(d, "other.txt"), "w").write("x")
        sh("git", "add", "."); sh("git", "commit", "-q", "-m", "base"); sh("git", "checkout", "-q", "-b", "work")
        open(os.path.join(d, pages, "B.json"), "w").write('{"items": []}')          # 已跟踪、改动（工作区）
        open(os.path.join(d, pages, "C.json"), "w").write("{}")                     # 未跟踪的新页
        open(os.path.join(d, "docs/modernization/rules/decide/sheets", "S.json"), "w").write("{}")
        open(os.path.join(d, pages, "D.txt"), "w").write("x")
        found = [os.path.relpath(x, d) for x in changed_pages("main", d)]
        assert found == [pages + "/B.json", pages + "/C.json", "docs/modernization/rules/decide/sheets/S.json"], found
        try:
            changed_pages("no-such-ref", d)
            raise AssertionError("无效基线应失败")
        except SystemExit as e:
            assert "取不到基线" in str(e)
    # 列表形式的页、用语审定页、同一问题只报一次
    with _t.TemporaryDirectory() as d:
        p = os.path.join(d, "L.json")
        json.dump([item(), item(no="T-2", basis=[])], open(p, "w", encoding="utf-8"), ensure_ascii=False)
        assert len(check_file(p, names, vocab)) == 1
        json.dump({"terms_review": True, "items": [item(title="合同老叫法「承担部门」")]}, open(p, "w", encoding="utf-8"), ensure_ascii=False)
        assert check_file(p, names, vocab) == []
        json.dump({"items": [item(title="合同老叫法", opts=[dict(label="老叫法", effect="", rec=True)])]}, open(p, "w", encoding="utf-8"), ensure_ascii=False)
        assert len([e for e in check_file(p, names, vocab) if "老叫法" in e]) == 1   # 标题和选项里都有同一个方言，只报一次
    # 整张页
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "P.json")
        json.dump({"sheet": "P", "items": [item(), item(no="T-2", basis=[])]}, open(p, "w", encoding="utf-8"), ensure_ascii=False)
        out = check_file(p, names, vocab)
        assert len(out) == 1 and out[0].startswith("P.json T-2: R2"), out
        open(p, "w").write("{")
        assert check_file(p, names, vocab)[0].startswith("P.json: 不是合法 JSON")
    # 词表切词：单字连接词不能开头或结尾
    assert unregistered("合同的收入", vocab, set()) == [] and unregistered("的收入", vocab, set()) != [] and unregistered("收入的", vocab, set()) != []
    # 已答依据随 check_pages 同在 rules/decide（G2）：路径错了，已答页会被当成未答整批报错
    assert os.path.isdir(ANSWERS_DIR) and os.path.isfile(ANSWERED_REGISTRY), (ANSWERS_DIR, ANSWERED_REGISTRY)
    real = load_vocab()
    if real:
        assert "承担部门" not in real["words"] and real["longest"] >= 2
    print("check_pages self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        sys.exit(main(sys.argv[1:]))
