#!/usr/bin/env python3
"""提交复审结论到 PR，并增删「复审通过」标签（定稿 1.8，H21；机读头约定见 workorders/README 第 10 节）。

用法（复审子代理自己执行）：post_review.py --pr N --body-file F [--guard] [--repo OWNER/REPO]
F 头三行即机读头（结论、commit、复审者），其后空一行接正文；结论只从文件解析，不接受命令行手填，作者无从改判。
解析不过即拒绝；再核 PR head 等于头里的 commit、通过结论另核关联 Issue 已排期（issue_gate.py，二乙 #24；必改不拦。已知缺口：结论提交后再改 PR 正文加关联管不到，合入时复核属队列改造、推后；PR 指向非默认分支时关闭集合可能为空，多拒而非门坏），然后把 F 原样作为普通 review（COMMENT，commit_id=该 commit）提交。
review 成功后才动标签：必改 → 去；首轮通过 → 加；终审通过 → 不动。--guard（钩子/安全类，派工人声明）
须同 commit 首轮与终审最新结论都为通过才加，缺一份就等。加标签前再核 head 与返回的 commit_id。mq 对守护路径仍自查。
本脚本是仓库里唯一写「复审通过」标签的地方（H21）。退出码：0 成功（含等另一份结论）；2 参数、policy、gh、head 或关联 Issue 不过
（未提交）；3 review 提交失败（标签未动）；4 review 已提交但标签未按预期完成。
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

import issue_gate

POLICY = pathlib.Path(__file__).resolve().parents[2] / "policy.json"
SHA = re.compile(r"[0-9a-f]{40}")
REPO = re.compile(r"[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")
CTRL = re.compile("[\\x00-\\x1f\\x7f-\\x9f\\u2028\\u2029]")
MODEL = re.compile(r"[A-Za-z0-9._]+")
IDENT = re.compile("[^\\s:：\\x00-\\x1f\\x7f-\\x9f\\u2028\\u2029]+")
# 结论与复审者类别的字面值：workorders/README 第 10 节约定，与 mq 共用；不按 policy.json 列表位置取语义
PASS, FIX, FIRST, FINAL = "通过", "必改", "首轮", "终审"
ADD_FLAG, REMOVE_FLAG = "--add-label", "--remove-label"
EXIT_REFUSED, EXIT_REVIEW_FAILED, EXIT_LABEL_FAILED = 2, 3, 4


class Refused(Exception):
    """拒绝或失败；args[0] 为退出码，args[1] 为说明。"""


def load_policy(path=POLICY):
    try:
        data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
        review, labels = data["review"], data["labels"]
        ok = (set(review["verdicts"]) == {PASS, FIX} and len(review["header_fields"]) == 3
              and all(isinstance(f, str) and f for f in review["header_fields"])
              and isinstance(labels["review_passed"], str))
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise Refused(EXIT_REFUSED, f"policy.json 读不到或缺键（{type(exc).__name__}: {exc}）") from None
    if not ok:
        raise Refused(EXIT_REFUSED, "policy.json 不合约定：verdicts 须恰为通过/必改，header_fields 3 项，标签名为字符串")
    return review, labels


def gh(args, stdin=None):
    """调用 gh，返回 (退出码, 标准输出)；gh 不存在返回 127。"""
    try:
        proc = subprocess.run(["gh", *args], input=stdin, capture_output=True, text=True, encoding="utf-8")
    except OSError as exc:
        sys.stderr.write(f"无法运行 gh（{exc.strerror}）\n")
        return 127, ""
    if proc.returncode != 0 and proc.stderr: sys.stderr.write(proc.stderr)
    return proc.returncode, proc.stdout


def loads(rc, out):
    """gh 成功且输出为 JSON 时返回解析结果，否则 None。"""
    try:
        return json.loads(out) if rc == 0 else None
    except (ValueError, TypeError):
        return None


def parse_reviewer(value):
    """<模型>-<类别>-<标识>：按 - 切成 3 段，第 2 段须精确等于首轮或终审；返回类别或 None。"""
    parts = value.split("-", 2) if isinstance(value, str) else []
    if len(parts) != 3 or parts[1] not in (FIRST, FINAL):
        return None
    return parts[1] if MODEL.fullmatch(parts[0]) and IDENT.fullmatch(parts[2]) else None


def parse_header(text, fields=None):
    """按 README 第 10 节解析机读头；合格返回 {verdict, commit, reviewer, kind}，否则 None。"""
    lines = text.split("\n")[:3] if isinstance(text, str) else []
    fields, values = fields or load_policy()[0]["header_fields"], []
    for field, line in zip(fields, lines):
        m = None if CTRL.search(line) else re.fullmatch(re.escape(field) + r"[：:](.*)", line.strip())   # other newline characters make it invalid
        if m is None:
            return None
        values.append(m.group(1).strip())
    if len(values) != 3:
        return None
    verdict, commit, reviewer = values
    kind = parse_reviewer(reviewer)
    if verdict not in (PASS, FIX) or not SHA.fullmatch(commit) or kind is None:
        return None
    return {"verdict": verdict, "commit": commit, "reviewer": reviewer, "kind": kind}


def read_header(pr, text, fields, repo):
    """校验参数与正文文件头，返回 parse_header 的结果；不合格抛 Refused（退出码 2）。"""
    lines = text.split("\n") if isinstance(text, str) else []
    for bad, why in ((pr <= 0, "PR 号须为正整数"),
                     (repo is not None and not REPO.fullmatch(repo), "--repo 须为 OWNER/REPO"),
                     (len(lines) > 3 and lines[3].strip() != "", "机读头三行之后须空一行再接正文")):
        if bad:
            raise Refused(EXIT_REFUSED, why)
    header = parse_header(text, fields)
    if header is None:
        raise Refused(EXIT_REFUSED, f"正文文件头三行不是合格机读头：{fields[0]}：通过|必改／{fields[1]}：40 位小写 sha／"
                                    f"{fields[2]}：<模型>-<首轮|终审>-<标识>（README 第 10 节）")
    return header


def current_head(pr, run, scope):
    """读 PR 当前 head；读不到或格式不对返回 None。"""
    data = loads(*run(["pr", "view", str(pr), *scope, "--json", "headRefOid"]))
    head = data.get("headRefOid") if isinstance(data, dict) else None
    return head if isinstance(head, str) and SHA.fullmatch(head) else None


def latest_verdicts(pr, commit, run, slug, fields):
    """读 PR 上全部 review，返回同 commit 每类复审者的最新结论 {类别: 结论}；读不到或结构不对返回 None（与「读到了但没有」的 {} 区分）。"""
    pages = loads(*run(["api", "--paginate", "--slurp", f"repos/{slug}/pulls/{pr}/reviews"]))
    if not (isinstance(pages, list) and all(isinstance(p, list) for p in pages)):
        return None
    found = {}
    for item in (x for page in pages for x in page):   # GitHub 按提交时间升序返回，后者覆盖前者
        h = parse_header(item.get("body"), fields) if isinstance(item, dict) else None
        if h and h["commit"] == commit and item.get("commit_id") == commit:
            found[h["kind"]] = h["verdict"]
    return found


def label_present(pr, label, run, scope):
    """去标签失败后复查；只有 labels 为带 name 的字典列表才算读到，其余按仍在处理（失败关闭）。"""
    data = loads(*run(["pr", "view", str(pr), *scope, "--json", "labels"]))
    items = data.get("labels") if isinstance(data, dict) else None
    return any(x["name"] == label for x in items) if isinstance(items, list) and all(isinstance(x, dict) and isinstance(x.get("name"), str) for x in items) else True


def post(pr, text, run=None, policy=None, guard=False, repo=None):
    """按模块说明执行；text 为正文文件全文。成功返回说明文字，失败抛 Refused。run 缺省为 gh（自测传桩）。"""
    run = run or gh
    review, labels = policy or load_policy()
    fields = review["header_fields"]
    h = read_header(pr, text, fields, repo)
    verdict, commit, reviewer, kind = h["verdict"], h["commit"], h["reviewer"], h["kind"]
    scope, slug = (["-R", repo] if repo else []), repo or "{owner}/{repo}"
    head = current_head(pr, run, scope)
    if head is None:
        raise Refused(EXIT_REFUSED, f"读不到 PR #{pr} 的当前 head（或 gh 不可用），拒绝提交")
    if head != commit:
        raise Refused(EXIT_REFUSED, f"head 已变：PR #{pr} 当前 head {head}，被审 {commit}；未提交，请复审新 head")
    if verdict == PASS and (why := issue_gate.unscheduled(pr, run, scope)): raise Refused(EXIT_REFUSED, why)
    payload = json.dumps({"event": "COMMENT", "commit_id": commit, "body": text}, ensure_ascii=False)
    rc, out = run(["api", "--method", "POST", f"repos/{slug}/pulls/{pr}/reviews", "--input", "-"], stdin=payload)
    if rc != 0:
        raise Refused(EXIT_REVIEW_FAILED, f"review 提交失败（gh 退出码 {rc}），标签未动")
    done, label = f"review 已提交（{verdict}，{reviewer}）", labels["review_passed"]
    if verdict == FIX:
        rc, _ = run(["pr", "edit", str(pr), *scope, REMOVE_FLAG, label])
        if rc != 0 and label_present(pr, label, run, scope):
            raise Refused(EXIT_LABEL_FAILED, f"{done}，但去标签「{label}」失败，请重跑或手工核对")
        return f"{done}；标签「{label}」已去"
    if kind == FINAL and not guard:
        return f"{done}；终审通过只记录，不改标签"
    if guard:
        if (seen := latest_verdicts(pr, commit, run, slug, fields)) is None:
            raise Refused(EXIT_LABEL_FAILED, f"{done}，但读不到同 commit 的其他结论，标签未加，请重跑")
        seen[kind] = verdict
        if not (seen.get(FIRST) == PASS and seen.get(FINAL) == PASS):
            return f"{done}；守护类须同 commit 首轮与终审都通过，等另一份结论，标签未加"
    sent = loads(0, out)
    if (isinstance(sent, dict) and sent.get("commit_id") not in (None, commit)) or current_head(pr, run, scope) != commit:
        raise Refused(EXIT_LABEL_FAILED, f"{done}，但 head 或 review 的 commit 已不是 {commit}，未加标签；请复审新 head")
    rc, _ = run(["pr", "edit", str(pr), *scope, ADD_FLAG, label])
    if rc != 0:
        raise Refused(EXIT_LABEL_FAILED, f"{done}，但加标签「{label}」失败，请重跑或手工核对")
    return f"{done}；标签「{label}」已加"


def main(argv=None):
    ap = argparse.ArgumentParser(description="复审子代理提交复审结论（正文文件头三行为机读头）并增删复审通过标签")
    ap.add_argument("--pr", type=int, required=True)
    ap.add_argument("--body-file", required=True, help="头三行机读头，空一行后接正文；整份原样作为 review 正文")
    ap.add_argument("--guard", action="store_true", help="钩子/安全类：须同 commit 首轮与终审都通过才加标签")
    ap.add_argument("--repo", help="OWNER/REPO，透传给 gh")
    args = ap.parse_args(argv)
    try:
        text = pathlib.Path(args.body_file).read_text(encoding="utf-8-sig")
    except (OSError, ValueError) as exc:
        print(f"拒绝：读不到正文文件（{exc.strerror if isinstance(exc, OSError) else '非 UTF-8'}）", file=sys.stderr)
        return EXIT_REFUSED
    try:
        print(post(args.pr, text, guard=args.guard, repo=args.repo))
    except Refused as exc:
        print(f"{'拒绝' if exc.args[0] == EXIT_REFUSED else '失败'}：{exc.args[1]}", file=sys.stderr)
        return exc.args[0]
    return 0


if __name__ == "__main__":
    sys.exit(main())
