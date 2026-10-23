#!/usr/bin/env python3
"""文档用语守护：不得用「PSA」指代系统。

用户 2026-10-14：「当前仓库的所有文档里要避免使用 psa 旧名称，FORCITIS one 是新名称」；
「老系统称「老系统」，字面标识保留」；「文档标题改，仓库名和会话名暂不改」。
写法：新系统 → FORCITIS one；老系统 → 老系统（英文 the legacy system）。

用法：python3 docs/modernization/check_legacy_name.py   有违规时列出并以 1 退出（CI 使用）

扫描范围：仓库内被 git 跟踪的 .md / .yaml / .yml / .py 文本（docs/、根目录、backend/、frontend/）。
只查大写词元 PSA（前后不是字母、数字、_、-）；psa_*、psa-backend、forcitis-one-psa、psa-* 会话名等标识符不受影响。

例外（不报）：
1. 冻结或只追加的记录：已采纳 ADR 正文 adr/src/ADR-*.md 及生成的 adr/ADR-*.md、adr/README.md、adr/INDEX.md（SESSION_PLAYBOOK 附 M3「ADR」）；
   session-log/ 片段与 SESSION_LOG.md（只追加）。
2. 生成产物（检查其片段 / 模板即可）：INTENT.md、STATUS.md、MODERNIZATION_BRIEF.md。
3. 证据与机器产物目录：rules/raw/、map/、runtime/、u8c/。
4. 行内字面：反引号内、代码块内、URL 中；「」或“”引号内（用户原话、引用的字面值）；intent 片段「## 原话」一节。
5. 软件类别：PSA 指 Professional Services Automation 这一类软件时保留。判定：同一行含
   Kantata / Certinia / Deltek / 行业通行 / 通行指标 / 「PSA 行业」 / Professional Services Automation，或是术语来源引用（refs）。
6. 显式白名单 glossary/legacy-name-allowlist.txt：每行「路径<Tab>子串」，该路径中含此子串的行不报（用于数据值，如 U8C 摘要前缀「PSA系统」）。
"""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MOD = "docs/modernization/"
TOKEN = re.compile(r"(?<![A-Za-z0-9_\-/.])PSA(?![A-Za-z0-9_\-])")
INDUSTRY = re.compile(r"Kantata|Certinia|Deltek|行业通行|通行指标|PSA\s*行业|Professional Services Automation|refs\s*=|\[\s*\"PSA\"")
SKIP_FILES = {MOD + f for f in ("INTENT.md", "STATUS.md", "MODERNIZATION_BRIEF.md", "SESSION_LOG.md", "adr/README.md", "adr/INDEX.md")}
SKIP_DIRS = tuple(MOD + d for d in ("session-log/", "rules/raw/", "map/", "runtime/", "u8c/"))
ADR_BODY = re.compile(r"^docs/modernization/adr/(src/)?ADR-\d{4}\.md$")
EXTS = (".md", ".yaml", ".yml", ".py")
SELF = MOD + "check_legacy_name.py"


def allowlist():
    path = os.path.join(ROOT, MOD, "glossary/legacy-name-allowlist.txt")
    rules = []
    if os.path.exists(path):
        for line in open(path, encoding="utf-8"):
            if line.strip() and not line.startswith("#"):
                p, _, s = line.rstrip("\n").partition("\t")
                rules.append((p, s))
    return rules


def in_scope(f):
    return (f.endswith(EXTS) and f != SELF and f not in SKIP_FILES and not f.startswith(SKIP_DIRS)
            and not ADR_BODY.match(f) and f.startswith(("docs/", "backend/", "frontend/", "CLAUDE.md", "README.md"))
            and "/node_modules/" not in f)


def violations(f, text, allowed):
    out, fence, section = [], False, ""
    is_intent = f.startswith(MOD + "intent/")
    for no, line in enumerate(text.split("\n"), 1):
        if line.startswith("```"):
            fence = not fence
            continue
        if line.startswith("## "):
            section = line
        if fence or (is_intent and section.startswith("## 原话")) or INDUSTRY.search(line):
            continue
        if any(f == p and s in line for p, s in allowed):
            continue
        for m in TOKEN.finditer(line):
            pre = line[:m.start()]
            if pre.count("`") % 2 or re.search(r"https?://\S*$", pre):
                continue
            if pre.count("「") > pre.count("」") or pre.count("“") > pre.count("”"):
                continue
            out.append((no, line.strip()))
            break
    return out


def main():
    files = subprocess.run(["git", "-C", ROOT, "-c", "core.quotepath=off", "ls-files", "-z"],
                           capture_output=True, text=True, check=True).stdout.split("\0")
    allowed, found = allowlist(), []
    for f in filter(in_scope, filter(None, files)):
        try:
            text = open(os.path.join(ROOT, f), encoding="utf-8").read()
        except (UnicodeDecodeError, FileNotFoundError):
            continue
        found += [(f, no, line) for no, line in violations(f, text, allowed)]
    for f, no, line in found:
        print(f"{f}:{no}: {line[:160]}")
    if found:
        sys.exit(f"\n{len(found)} 行用「PSA」指代系统：新系统写 FORCITIS one，老系统写「老系统」（英文 the legacy system）；"
                 "字面标识放进反引号，确属数据值的登记到 glossary/legacy-name-allowlist.txt。规则见本脚本说明。")
    print("check_legacy_name: 无违规")


if __name__ == "__main__":
    main()
