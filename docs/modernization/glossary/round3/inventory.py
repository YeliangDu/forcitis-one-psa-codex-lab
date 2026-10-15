#!/usr/bin/env python3
"""第三轮用语确认的盘点：列出 backend/glossary/proposed/*.yaml 中待确认的业务用语（status: proposed、kind 不是 error_code），
并标出与其他提议条目、正式词表（docs/modernization/glossary/terms.yaml）在 id、中文名、英文名、snake 上的重名，
以及在途使用：现有代码（backend/src、frontend）、派工卡与下一批计划、近 2 天有提交且未合并的远端分支的代码改动中是否出现。
派工卡按中文名、snake、英文名文本比对；下一批计划只认反引号里写出的 snake 或英文名（计划正文的普通字眼不算）。
在途分支里新增或改动的提议条目一并列出，标明来自哪个分支（合并前仍以分支为准，main 上已有同 id 的以 main 为准）。
代码与分支按标识符拆词后比对 snake 的连续词序列（与 GlossaryTest 拆词一致的近似），文档按中文名、snake、英文名文本比对。
输出 round3/inventory.json（逐条，供起草与生成决策单）；标准输出打印汇总。需要先 git fetch。
用法：python3 docs/modernization/glossary/round3/inventory.py"""
import collections, glob, json, os, re, subprocess, sys
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
PROPOSED = os.path.join(ROOT, "backend", "glossary", "proposed")
OFFICIAL = os.path.join(ROOT, "docs", "modernization", "glossary", "terms.yaml")
OUT = os.path.join(HERE, "inventory.json")
FIELDS = ("id", "zh", "en", "snake")
CODE_ROOTS = ("backend/src", "frontend")
CODE_SUFFIXES = (".java", ".ts", ".tsx", ".vue", ".sql", ".yaml", ".yml", ".properties", ".json", ".xml")
SKIP_DIRS = {"node_modules", "dist", "target", ".pnpm"}
PLAN_DOCS = ("docs/modernization/phase1a/cards", "docs/modernization/phase1a/NEXT_BATCH_PLAN.md")
CODE_ONLY_DOCS = ("docs/modernization/phase1a/NEXT_BATCH_PLAN.md",)
IN_FLIGHT_DAYS = 2
MAX_NGRAM = 6
IDENT = re.compile(r"[A-Za-z][A-Za-z0-9_-]*")


def words(identifier):
    return [w.lower() for w in re.findall(r"[A-Z]+(?![a-z])|[A-Z]?[a-z]+|[0-9]+", identifier)]


def ngrams(text):
    found = set()
    for identifier in IDENT.findall(text):
        ws = words(identifier)
        for i in range(len(ws)):
            for n in range(1, min(MAX_NGRAM, len(ws) - i) + 1):
                found.add(tuple(ws[i:i + n]))
    return found


def code_ngrams():
    found = set()
    for top in CODE_ROOTS:
        for directory, dirs, files in os.walk(os.path.join(ROOT, top)):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
            for name in files:
                if name.endswith(CODE_SUFFIXES):
                    with open(os.path.join(directory, name), encoding="utf-8", errors="replace") as f:
                        found |= ngrams(f.read())
    return found


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout


def in_flight():
    branches = {}
    for line in git("for-each-ref", "--format=%(refname:short) %(committerdate:unix)", "refs/remotes/origin").splitlines():
        ref, stamp = line.rsplit(" ", 1)
        if ref in ("origin/main", "origin/HEAD", "origin/generated-docs") or ref == "origin":
            continue
        if int(stamp) < int(git("log", "-1", "--format=%ct", "origin/main").strip()) - IN_FLIGHT_DAYS * 86400:
            continue
        if not git("rev-list", "--max-count=1", f"origin/main..{ref}").strip():
            continue
        added = "\n".join(l[1:] for l in git("diff", f"origin/main...{ref}", "--", *CODE_ROOTS).splitlines() if l.startswith("+") and not l.startswith("+++"))
        branches[ref[len("origin/"):]] = (ngrams(added), added)
    return branches


def branch_proposals(branches, known):
    found = []
    for branch in sorted(branches):
        ref = f"origin/{branch}"
        for path in git("diff", "--name-only", "--diff-filter=AM", f"origin/main...{ref}", "--", "backend/glossary/proposed/").splitlines():
            if not path.endswith(".yaml"):
                continue
            document = yaml.safe_load(git("show", f"{ref}:{path}")) or {}
            for term in document.get("terms") or []:
                if term.get("status") == "proposed" and term.get("kind") != "error_code" and term["id"] not in known:
                    found.append((os.path.basename(path), term, branch))
                    known.add(term["id"])
    return found


def plan_texts():
    texts = {}
    for entry in PLAN_DOCS:
        path = os.path.join(ROOT, entry)
        paths = sorted(glob.glob(os.path.join(path, "*.md"))) if os.path.isdir(path) else [path]
        for p in paths:
            with open(p, encoding="utf-8") as f:
                texts[os.path.relpath(p, ROOT)] = f.read()
    if not texts:
        sys.exit("找不到派工卡与下一批计划，检查 PLAN_DOCS")
    return texts


def doc_mentions(term, text, code_only=False):
    snake, en, zh = term.get("snake") or "", term.get("en") or "", term.get("zh") or ""
    if code_only:
        return any(re.search(rf"`[^`]*(?<![A-Za-z0-9_]){re.escape(w)}(?![A-Za-z0-9_])[^`]*`", text) for w in (snake, en) if w)
    return bool((snake and re.search(rf"(?<![A-Za-z0-9_]){re.escape(snake)}(?![A-Za-z0-9_])", text))
                or (en and re.search(rf"(?<![A-Za-z0-9]){re.escape(en)}(?![a-z0-9])", text))
                or (len(zh) >= 3 and zh in text))


def keyed(term, field):
    value = str(term.get(field) or "").strip()
    return value.lower() if field in ("en", "snake") else value


def main():
    official = yaml.safe_load(open(OFFICIAL, encoding="utf-8"))["terms"]
    items, all_proposed = [], []
    for path in sorted(glob.glob(os.path.join(PROPOSED, "*.yaml"))):
        name = os.path.basename(path)
        for term in yaml.safe_load(open(path, encoding="utf-8")).get("terms") or []:
            all_proposed.append((name, term))
            if term.get("status") == "proposed" and term.get("kind") != "error_code":
                items.append((name, term, None))
    if not items:
        sys.exit("没有待确认的提议条目，检查 proposed 目录")

    index = {f: collections.defaultdict(list) for f in FIELDS}
    for t in official:
        for f in FIELDS:
            if keyed(t, f):
                index[f][keyed(t, f)].append(("terms.yaml", t))
    for name, t in all_proposed:
        for f in FIELDS:
            if keyed(t, f):
                index[f][keyed(t, f)].append((name, t))

    code, branches, plans = code_ngrams(), in_flight(), plan_texts()
    extra = branch_proposals(branches, {t["id"] for _, t in all_proposed} | {t["id"] for t in official})
    for name, t, branch in extra:
        for f in FIELDS:
            if keyed(t, f):
                index[f][keyed(t, f)].append((f"{name}@{branch}", t))
    items += extra
    rows, counts = [], collections.Counter()
    for name, t, branch in items:
        key = tuple(str(t.get("snake") or t["id"]).lower().split("_"))
        usage = {"code": key in code,
                 "branches": sorted(b for b, (grams, _) in branches.items() if key in grams),
                 "plans": sorted(p for p, text in plans.items() if doc_mentions(t, text, p in CODE_ONLY_DOCS))}
        clashes = []
        for f in FIELDS:
            for other_file, other in index[f][keyed(t, f)]:
                if other is t:
                    continue
                clashes.append({"field": f, "file": other_file, "id": other["id"], "zh": other.get("zh"), "en": other.get("en"),
                                "bc": other.get("bc"), "status": other.get("status"), "kind": other.get("kind"),
                                "definition": other.get("definition")})
                counts[(f, "official" if other_file == "terms.yaml" else "proposed")] += 1
        rows.append({"key": f"{name}#{t['id']}", "file": name, "branch": branch, **{k: t.get(k) for k in
                     ("id", "zh", "en", "snake", "kind", "bc", "definition", "standard_ref", "aliases", "forbidden", "note")},
                     "clashes": clashes, "usage": usage})
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(rows, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"待确认 {len(rows)} 条（其中在途分支新增 {len(extra)} 条），按模块：" + "、".join(f"{bc} {n}" for bc, n in collections.Counter(r["bc"] for r in rows).most_common()))
    print(f"有重名的 {sum(1 for r in rows if r['clashes'])} 条；重名次数：" +
          "、".join(f"{f}×{side} {n}" for (f, side), n in sorted(counts.items())))
    print(f"在途使用：代码 {sum(r['usage']['code'] for r in rows)} 条、未合并分支 {sum(bool(r['usage']['branches']) for r in rows)} 条"
          f"（{len(branches)} 个分支）、派工卡与计划 {sum(bool(r['usage']['plans']) for r in rows)} 条")
    print(f"写出 {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
