#!/usr/bin/env python3
"""由 proposed/*.yaml 生成 proposed/README.md（提议术语清单），产物不手改。

用法：
  python3 backend/glossary/build_proposed_index.py              重新生成 README.md
  python3 backend/glossary/build_proposed_index.py --self-test  解析器与渲染规则自测

由 docs/modernization/build_all.py 调用；build_all.py --check 要求生成后 README.md 无差异。
CI 的 python3 不带 PyYAML，故只用标准库解析本目录 yaml 用到的子集（块映射、块序列、单行字符串与 true / false / null、空的 {} 与由简单值组成的单行 [...]），
遇到子集以外的写法即失败，不猜测。

每个 yaml 文件一节：文件开头连续的 # 行是该节导语（每行一段），遇到第一个非 # 行即停止。
表格收 terms 中除 error_code 以外的全部条目；「是否疑似方言」由 aliases 中类型为 dialect / undetermined 的别名
与 forbidden 生成，「备注」为 note 原文。
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROPOSED = os.path.join(HERE, "proposed")
README = os.path.join(PROPOSED, "README.md")

HEADER = """# 提议术语清单（第三轮用语确认用）

<!-- 本文件由 backend/glossary/build_proposed_index.py 从同目录 *.yaml 生成，不要手改；改 yaml 后运行 python3 docs/modernization/build_all.py 并提交结果。 -->

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。本表按文件列出除错误码以外的全部提议术语；「是否疑似方言」由条目中类型为方言或待定的别名与禁用写法生成，空表示未登记问题；「备注」为条目 note 原文。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）与 `python3 docs/modernization/build_all.py`（重新生成本文件）。
"""


class YamlSubsetError(ValueError):
    pass


# SnakeYAML（GlossaryTest）按 YAML 1.1 解析：这些未加引号的值不是字符串，本解析器不解释它们，一律要求加引号
YAML11_SPECIAL = re.compile(
    r"y|Y|yes|Yes|YES|n|N|no|No|NO|True|TRUE|False|FALSE|on|On|ON|off|Off|OFF|Null|NULL"
    r"|[-+]?[0-9][0-9a-fA-FxXoObB_:.eE+-]*"
    r"|[-+]?\.[0-9][0-9_]*([eE][-+]?[0-9]+)?"
    r"|[-+]?\.(inf|Inf|INF)|\.(nan|NaN|NAN)"
    r"|[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}([Tt ].*)?")
KEY = re.compile(r"^([A-Za-z_][A-Za-z0-9_]*):(?: (.*))?$")


def scalar(text, where):
    if text == "[]":
        return []
    if text == "{}":
        return {}
    if text.startswith("[") and text.endswith("]"):
        items = [item.strip() for item in text[1:-1].split(",")]
        if any(not item or re.search(r"[\[\]{}'\"#&*!|>?]", item) for item in items):
            raise YamlSubsetError(f"{where}: 单行序列只支持不加引号的简单值")
        return [scalar(item, where) for item in items]
    if text.startswith('"'):
        if not text.endswith('"') or len(text) < 2:
            raise YamlSubsetError(f"{where}: 双引号字符串未闭合")
        try:
            return json.loads(text)
        except ValueError as e:
            raise YamlSubsetError(f"{where}: 双引号字符串含不支持的转义：{e}")
    if text.startswith("'"):
        if not text.endswith("'") or len(text) < 2:
            raise YamlSubsetError(f"{where}: 单引号字符串未闭合")
        body = text[1:-1]
        if re.search(r"(?<!')'(?!')", body.replace("''", "")):
            raise YamlSubsetError(f"{where}: 单引号字符串含未成对的引号")
        return body.replace("''", "'")
    if text[0] in "[{&*!|>%@`#" or text[:2] in ("- ", "? ") or text in ("-", "?", "=", "<<"):
        raise YamlSubsetError(f"{where}: 不支持以 {text[:2]!r} 开头的写法")
    if re.search(r"\s#", text) or text.endswith(":") or ": " in text:
        raise YamlSubsetError(f"{where}: 未加引号的值含「 #」或「: 」，含义有歧义")
    if text in ("true", "false"):
        return text == "true"
    if text in ("null", "~"):
        return None
    if YAML11_SPECIAL.fullmatch(text):
        raise YamlSubsetError(f"{where}: 值 {text!r} 可能被 YAML 1.1 读成非字符串，请加引号")
    return text


def parse_yaml(text, name="<yaml>"):
    lines = []
    for number, raw in enumerate(text.splitlines(), 1):
        if raw.strip() == "" or raw.lstrip().startswith("#"):
            continue
        if "\t" in raw[: len(raw) - len(raw.lstrip())]:
            raise YamlSubsetError(f"{name}:{number}: 缩进含制表符")
        indent = len(raw) - len(raw.lstrip(" "))
        lines.append((indent, raw[indent:].rstrip(), f"{name}:{number}"))
    position = 0

    def block(indent):
        nonlocal position
        if position >= len(lines) or lines[position][0] < indent:
            return None
        if lines[position][0] > indent:
            raise YamlSubsetError(f"{lines[position][2]}: 缩进不对齐")
        return sequence(indent) if lines[position][1].startswith("- ") or lines[position][1] == "-" else mapping(indent)

    def value_after_key(indent, rest, where):
        nonlocal position
        if rest is not None and rest != "":
            return scalar(rest, where)
        if position < len(lines):
            next_indent, next_text, _ = lines[position]
            if next_indent > indent:
                return block(next_indent)
            if next_indent == indent and next_text.startswith("- "):
                return sequence(indent)
        raise YamlSubsetError(f"{where}: 键没有值（空值请写 '' 或 []）")

    def mapping(indent, first=None):
        nonlocal position
        result = {}
        while True:
            if first is not None:
                text, where = first
                first = None
            elif position < len(lines) and lines[position][0] == indent and not lines[position][1].startswith("- "):
                _, text, where = lines[position]
                position += 1
            else:
                break
            m = KEY.match(text)
            if not m:
                raise YamlSubsetError(f"{where}: 不是「键: 值」行")
            if m.group(1) in result:
                raise YamlSubsetError(f"{where}: 重复的键 {m.group(1)}")
            result[m.group(1)] = value_after_key(indent, m.group(2), where)
        if position < len(lines) and lines[position][0] > indent:
            raise YamlSubsetError(f"{lines[position][2]}: 缩进不对齐")
        return result

    def sequence(indent):
        nonlocal position
        result = []
        while position < len(lines) and lines[position][0] == indent and lines[position][1].startswith("- "):
            _, text, where = lines[position]
            position += 1
            item = text[2:]
            if KEY.match(item):
                result.append(mapping(indent + 2, (item, where)))
            else:
                result.append(scalar(item, where))
        if position < len(lines) and lines[position][0] > indent:
            raise YamlSubsetError(f"{lines[position][2]}: 缩进不对齐")
        return result

    if not lines:
        return {}
    if lines[0][0] != 0:
        raise YamlSubsetError(f"{lines[0][2]}: 顶层不得缩进")
    document = block(0)
    if position != len(lines):
        raise YamlSubsetError(f"{lines[position][2]}: 无法解析的行")
    if not isinstance(document, dict):
        raise YamlSubsetError(f"{name}: 顶层必须是映射")
    return document


def header_comments(text):
    paragraphs = []
    for raw in text.splitlines():
        if not raw.startswith("#"):
            break
        line = raw[1:].strip()
        if line:
            paragraphs.append(line)
    return paragraphs


def cell(value):
    return str(value).replace("\\", "\\\\").replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def dialect_column(term):
    marks, named = [], set()
    forbidden = term.get("forbidden") or []
    for alias in term.get("aliases") or []:
        kind, alias_name = alias.get("type"), alias.get("name")
        if kind in ("dialect", "undetermined"):
            banned = alias.get("deprecated") is True or alias_name in forbidden
            marks.append(f"「{alias_name}」" + ("方言" if kind == "dialect" else "待定") + ("·已禁用" if banned else ""))
            named.add(alias_name)
    marks += [f"「{f}」已禁用" for f in forbidden if f not in named]
    return "；".join(marks)


def section(file_name, text):
    document = parse_yaml(text, file_name)
    unknown = set(document) - {"terms", "common_words"}
    if unknown:
        raise YamlSubsetError(f"{file_name}: 顶层只允许 terms、common_words，发现 {sorted(unknown)}")
    terms = document.get("terms", [])
    common_words = document.get("common_words", [])
    if not isinstance(terms, list) or not isinstance(common_words, list):
        raise YamlSubsetError(f"{file_name}: terms 与 common_words 须为序列")
    if not all(isinstance(w, str) for w in common_words):
        raise YamlSubsetError(f"{file_name}: common_words 的每项须为字符串")
    for index, term in enumerate(terms):
        where = f"{file_name}: terms[{index}]"
        if not isinstance(term, dict):
            raise YamlSubsetError(f"{where} 须为映射")
        missing = [k for k in ("id", "zh", "en", "definition", "kind", "bc") if term.get(k) in (None, "")]
        if missing:
            raise YamlSubsetError(f"{where} 缺少 {', '.join(missing)}")
        wrong = [k for k in ("id", "zh", "en", "definition", "kind", "bc", "note") if k in term and not isinstance(term[k], str)]
        if wrong:
            raise YamlSubsetError(f"{where} 的 {', '.join(wrong)} 须为字符串")
        aliases, forbidden = term.get("aliases", []), term.get("forbidden", [])
        if not isinstance(aliases, list) or not all(isinstance(a, dict) and isinstance(a.get("name"), str) for a in aliases):
            raise YamlSubsetError(f"{where} 的 aliases 须为带 name 的映射序列")
        if not isinstance(forbidden, list) or not all(isinstance(f, str) for f in forbidden):
            raise YamlSubsetError(f"{where} 的 forbidden 须为字符串序列")
    contexts = sorted({t["bc"] for t in terms})
    title = f"{contexts[0]}（`{file_name}`）" if len(contexts) == 1 else f"`{file_name}`"
    out = [f"## {title}", ""]
    if len(contexts) > 1:
        out += ["涉及模块：" + "、".join(f"`{c}`" for c in contexts) + "。", ""]
    for paragraph in header_comments(text):
        out += [paragraph, ""]
    rows = [t for t in terms if t["kind"] != "error_code"]
    if rows:
        out += ["| id | zh | en | 定义 | 是否疑似方言 | 备注 |", "|---|---|---|---|---|---|"]
        for t in rows:
            out.append(f"| `{cell(t['id'])}` | {cell(t['zh'])} | {cell(t['en'])} | {cell(t['definition'])} | "
                       f"{cell(dialect_column(t))} | {cell(t.get('note') or '')} |")
        out.append("")
    else:
        out += ["无错误码以外的术语。", ""]
    error_codes = len(terms) - len(rows)
    counts = []
    if error_codes:
        counts.append(f"错误码 {error_codes} 个（kind 为 `error_code`）")
    if common_words:
        counts.append(f"技术词 {len(common_words)} 个（`common_words`）")
    if counts:
        out += ["另有" + "、".join(counts) + "，不在表中。", ""]
    return out, len(rows)


def render(files):
    out, total = [HEADER], 0
    for file_name in sorted(files):
        lines, count = section(file_name, files[file_name])
        out.append("\n".join(lines))
        total += count
    return "\n".join(out).rstrip("\n") + "\n", total


def build():
    files = {}
    for file_name in sorted(os.listdir(PROPOSED)):
        if file_name.endswith(".yml"):
            sys.exit(f"{file_name}: 提议术语文件须以 .yaml 结尾（GlossaryTest 只读 .yaml）")
        if file_name.endswith(".yaml"):
            with open(os.path.join(PROPOSED, file_name), encoding="utf-8") as f:
                files[file_name] = f.read()
    if not files:
        sys.exit(f"{PROPOSED} 下没有 yaml，无法生成")
    content, total = render(files)
    with open(README, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"proposed/README.md: {len(files)} 个文件，{total} 条术语")


def self_test():
    failures = []

    def check(name, actual, expected):
        if actual != expected:
            failures.append(f"{name}: 期望 {expected!r}，实际 {actual!r}")

    def rejects(name, text):
        try:
            parse_yaml(text, name)
        except YamlSubsetError:
            return
        failures.append(f"{name}: 应拒绝但被接受")

    sample = """# 第一行导语
#
# 第二行导语
terms:
- id: tail_adjustment
  zh: 尾调
  en: TailAdjustment
  kind: entity
  bc: kernel
  definition: "报价 | 整单的人工调整额"
  aliases:
  - name: 尾差
    type: dialect
    deprecated: true
  - name: OH工时
    type: undetermined
  - name: 重做
    type: synonym
  forbidden:
  - 尾差
  - import_batch
  standard_ref: []
  note: 'it''s ok'
# 文件中间的注释不是导语
- id: error__x
  zh: 错
  en: X
  kind: error_code
  bc: kernel
  definition: 错误（409）
common_words:
- batch
"""
    parsed = parse_yaml(sample, "sample")
    check("顶层键", sorted(parsed), ["common_words", "terms"])
    check("双引号", parsed["terms"][0]["definition"], "报价 | 整单的人工调整额")
    check("单引号转义", parsed["terms"][0]["note"], "it's ok")
    check("布尔", parsed["terms"][0]["aliases"][0]["deprecated"], True)
    check("空序列", parsed["terms"][0]["standard_ref"], [])
    check("同级缩进的序列", parsed["terms"][0]["forbidden"], ["尾差", "import_batch"])
    check("顶层序列项数", len(parsed["terms"]), 2)
    check("导语：开头连续的 # 行，空 # 行跳过", header_comments(sample), ["第一行导语", "第二行导语"])
    check("导语：遇到第一个非 # 行即停止", header_comments("# 甲\n\n# 乙\nterms: []\n"), ["甲"])
    check("导语：首行不是 # 时为空", header_comments("terms: []\n# 不是导语\n"), [])
    check("方言列", dialect_column(parsed["terms"][0]), "「尾差」方言·已禁用；「OH工时」待定；「import_batch」已禁用")
    check("方言列为空", dialect_column({"aliases": [], "forbidden": []}), "")
    check("待定且已弃用", dialect_column({"aliases": [{"name": "甲", "type": "undetermined", "deprecated": True}]}), "「甲」待定·已禁用")
    check("单元格转义", cell("a|b\nc"), "a\\|b c")
    lines, count = section("kernel.yaml", sample)
    check("错误码不入表", count, 1)
    check("节标题", lines[0], "## kernel（`kernel.yaml`）")
    check("导语在表前", lines[2:6], ["第一行导语", "", "第二行导语", ""])
    check("计数行", lines[-2], "另有错误码 1 个（kind 为 `error_code`）、技术词 1 个（`common_words`），不在表中。")
    multi, _ = section("mixed.yaml", "terms:\n- id: a\n  zh: 甲\n  en: A\n  kind: value\n  bc: x.y\n  definition: d\n"
                                     "- id: b\n  zh: 乙\n  en: B\n  kind: value\n  bc: a.b\n  definition: d\n")
    check("多模块节标题", multi[:3], ["## `mixed.yaml`", "", "涉及模块：`a.b`、`x.y`。"])
    rejects("块标量", "terms:\n- id: a\n  definition: |\n    多行\n")
    rejects("多行续行", "terms:\n- id: a\n  definition: 第一行\n    第二行\n")
    rejects("行内注释", "terms:\n- id: a # 注释\n")
    check("单行序列", parse_yaml("forbidden: [离职日期, separation_date]\n")["forbidden"], ["离职日期", "separation_date"])
    rejects("嵌套单行序列", "terms: [a, [b]]\n")
    rejects("单行序列含引号", "terms: [a, 'b']\n")
    rejects("单行序列含问号", "terms: [?7]\n")
    rejects("锚点", "terms: &x []\n")
    rejects("重复键", "terms: []\nterms: []\n")
    rejects("未闭合引号", "terms:\n- id: \"abc\n")
    for special in ("yes", "Yes", "ON", "Off", "NO", "TRUE", "Null", "12", "012", "08", "0x1F", "1_000", "+1", "1:30", "1.", "1.0e+5",
                    ".5", "-.inf", ".NaN", "2026-10-15", "2026-10-15 10:00"):
        rejects(f"YAML 1.1 特殊值 {special}", f"terms:\n- id: {special}\n")
    for odd in ("- x", "? x", "=", "<<", "a\t# c"):
        rejects(f"有歧义的值 {odd!r}", f"terms:\n- id: {odd}\n")
    check("普通文本", parse_yaml("a: 20 % 以上\nb: v1.2\nc: ADR-0031\n"), {"a": "20 % 以上", "b": "v1.2", "c": "ADR-0031"})
    rejects("缩进不对齐", "terms:\n- id: a\n   zh: 甲\n")
    good = "  zh: 甲\n  en: A\n  kind: value\n  bc: x.y\n  definition: d\n"
    for name, text in (("缺字段", "terms:\n- id: a\n  zh: 甲\n"),
                       ("common_words 不是序列", "common_words: batch\n"),
                       ("terms 是映射", "terms: {}\n"),
                       ("条目不是映射", "terms:\n- a\n"),
                       ("bc 不是字符串", "terms:\n- id: a\n  zh: 甲\n  en: A\n  kind: value\n  bc: [x]\n  definition: d\n"),
                       ("别名是字符串", "terms:\n- id: a\n" + good + "  aliases:\n  - 甲\n"),
                       ("禁用写法是映射", "terms:\n- id: a\n" + good + "  forbidden:\n  - name: 甲\n")):
        try:
            section("bad.yaml", text)
            failures.append(f"{name}: 应失败但通过")
        except YamlSubsetError:
            pass
    try:
        import yaml  # 本机有 PyYAML 时，与它逐文件比对解析结果；CI 的 python3 没有，跳过
    except ImportError:
        print("警告：本机无 PyYAML，跳过与 PyYAML 的逐文件比对", file=sys.stderr)
    else:
        for file_name in sorted(os.listdir(PROPOSED)):
            if file_name.endswith(".yaml"):
                with open(os.path.join(PROPOSED, file_name), encoding="utf-8") as f:
                    text = f.read()
                try:
                    check(f"与 PyYAML 一致：{file_name}", parse_yaml(text, file_name), yaml.safe_load(text) or {})
                except YamlSubsetError as e:
                    failures.append(f"解析 {file_name}: {e}")
    if failures:
        sys.exit("自测失败：\n  " + "\n  ".join(failures))
    print("self-test: ok")


if __name__ == "__main__":
    if "--self-test" in sys.argv[1:]:
        self_test()
    else:
        try:
            build()
        except YamlSubsetError as e:
            sys.exit(f"生成 proposed/README.md 失败：{e}")
