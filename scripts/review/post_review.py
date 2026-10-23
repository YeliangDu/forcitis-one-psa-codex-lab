#!/usr/bin/env python3
"""提交复审结论到 PR，并增删「复审通过」标签（定稿 1.8、2.3，硬约束 H21）。

用法：post_review.py --pr N --commit <40 位 sha> --verdict <结论> --reviewer <模型-标识> --body-file F

流程（任一步失败即停，失败关闭）：
1. 校验：结论须在 policy.json review.verdicts 里；sha 须为 40 位小写十六进制；正文文件可读。
2. 取 PR 当前 head（gh pr view N --json headRefOid）；读不到或与 --commit 不等就拒绝，什么也不提交。
3. 以普通 review（event=COMMENT，commit_id=--commit）提交：首三行为机读头（字段名取 review.header_fields），
   空一行后接正文文件内容。同一账号开的 PR 不能 approve / request changes，所以只能用 COMMENT。
4. review 提交成功后才动标签（labels.review_passed）：通过 → 加；必改 → 去（原本没有不算错）。
   review 提交失败则标签不动；标签操作失败时退出码非 0，并说明 review 已提交。

终审：reviewer 等于 review.final_reviewer_tag 时同样提交 review，但「通过」不加标签——标签表示首轮复审通过，
由首轮结论决定；mq 对终审类路径另查两条通过 review 是否都在。终审「必改」去标签，使 PR 在回改并重审前不能入队。

本脚本是仓库里唯一写「复审通过」标签的地方（H21；budget_check 检查写入点唯一），别处不得增删该标签。
所有数值与枚举（结论、机读头字段、标签名、终审标识）只从仓库根 policy.json 读。

退出码：0 成功；2 参数或 head 校验不过（未提交）；3 review 提交失败（标签未动）；4 review 已提交但标签操作失败。
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys

POLICY = pathlib.Path(__file__).resolve().parents[2] / "policy.json"
SHA = re.compile(r"[0-9a-f]{40}")
ADD_FLAG = "--add-label"
REMOVE_FLAG = "--remove-label"
EXIT_REFUSED, EXIT_REVIEW_FAILED, EXIT_LABEL_FAILED = 2, 3, 4


class Refused(Exception):
    """拒绝或失败；args[0] 为退出码，args[1] 为说明。"""


def load_policy(path=POLICY):
    data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    return data["review"], data["labels"]


def gh(args, stdin=None):
    """调用 gh，返回 (退出码, 标准输出)；失败时把 gh 的 stderr 转给调用者。"""
    proc = subprocess.run(["gh", *args], input=stdin, capture_output=True, text=True)
    if proc.returncode != 0 and proc.stderr:
        sys.stderr.write(proc.stderr)
    return proc.returncode, proc.stdout


def build_body(review, verdict, commit, reviewer, text):
    f = review["header_fields"]
    return f"{f[0]}：{verdict}\n{f[1]}：{commit}\n{f[2]}：{reviewer}\n\n{text}"


def validate(review, pr, commit, verdict, reviewer):
    if verdict not in review["verdicts"]:
        raise Refused(EXIT_REFUSED, f"结论「{verdict}」不在 policy.json review.verdicts 里")
    if not SHA.fullmatch(commit or ""):
        raise Refused(EXIT_REFUSED, "commit 须为 40 位小写十六进制 sha")
    if pr <= 0:
        raise Refused(EXIT_REFUSED, "PR 号须为正整数")
    if not reviewer.strip():
        raise Refused(EXIT_REFUSED, "复审者不能为空")


def current_head(pr, run):
    """读 PR 当前 head；读不到或格式不对返回 None（调用方据此拒绝）。"""
    rc, out = run(["pr", "view", str(pr), "--json", "headRefOid"])
    if rc != 0:
        return None
    try:
        head = json.loads(out).get("headRefOid")
    except (ValueError, AttributeError):
        return None
    return head if isinstance(head, str) and SHA.fullmatch(head) else None


def label_action(review, verdict, reviewer):
    """返回要做的标签动作：ADD_FLAG、REMOVE_FLAG，或 None（终审通过不动标签）。"""
    if verdict != review["verdicts"][0]:
        return REMOVE_FLAG
    return None if reviewer == review["final_reviewer_tag"] else ADD_FLAG


def label_present(pr, label, run):
    """去标签失败后复查；读不到按仍在处理（失败关闭）。"""
    rc, out = run(["pr", "view", str(pr), "--json", "labels"])
    if rc != 0:
        return True
    try:
        return any(item.get("name") == label for item in json.loads(out).get("labels", []))
    except (ValueError, AttributeError):
        return True


def post(pr, commit, verdict, reviewer, text, run=None, policy=None):
    """按模块说明执行；成功返回说明文字，失败抛 Refused。run 缺省为 gh（自测传桩）。"""
    run = run or gh
    review, labels = policy or load_policy()
    validate(review, pr, commit, verdict, reviewer)
    head = current_head(pr, run)
    if head is None:
        raise Refused(EXIT_REFUSED, f"读不到 PR #{pr} 的当前 head，拒绝提交")
    if head != commit:
        raise Refused(EXIT_REFUSED, f"head 已变：PR #{pr} 当前 head {head}，被审 {commit}；未提交")
    payload = json.dumps({"event": "COMMENT", "commit_id": commit,
                          "body": build_body(review, verdict, commit, reviewer, text)}, ensure_ascii=False)
    rc, _ = run(["api", "--method", "POST", f"repos/{{owner}}/{{repo}}/pulls/{pr}/reviews", "--input", "-"],
                stdin=payload)
    if rc != 0:
        raise Refused(EXIT_REVIEW_FAILED, f"review 提交失败（gh 退出码 {rc}），标签未动")
    action = label_action(review, verdict, reviewer)
    if action is None:
        return f"review 已提交（{verdict}，{reviewer}）；终审通过不改标签"
    label = labels["review_passed"]
    rc, _ = run(["pr", "edit", str(pr), action, label])
    if rc != 0 and action == REMOVE_FLAG and not label_present(pr, label, run):
        rc = 0   # 标签本来就没有：不算错
    if rc != 0:
        raise Refused(EXIT_LABEL_FAILED, f"review 已提交，但标签「{label}」{action} 失败，请重跑或手工核对")
    return f"review 已提交（{verdict}，{reviewer}）；标签「{label}」{action} 完成"


def main(argv=None):
    ap = argparse.ArgumentParser(description="提交复审结论（普通 review＋机读头）并增删复审通过标签")
    ap.add_argument("--pr", type=int, required=True)
    ap.add_argument("--commit", required=True)
    ap.add_argument("--verdict", required=True)
    ap.add_argument("--reviewer", required=True)
    ap.add_argument("--body-file", required=True)
    args = ap.parse_args(argv)
    try:
        text = pathlib.Path(args.body_file).read_text(encoding="utf-8")
    except OSError as exc:
        print(f"拒绝：读不到正文文件（{exc.strerror}）", file=sys.stderr)
        return EXIT_REFUSED
    try:
        print(post(args.pr, args.commit, args.verdict, args.reviewer, text))
    except Refused as exc:
        print(f"拒绝：{exc.args[1]}", file=sys.stderr)
        return exc.args[0]
    return 0


if __name__ == "__main__":
    sys.exit(main())
