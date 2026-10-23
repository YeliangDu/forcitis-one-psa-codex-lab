#!/usr/bin/env python3
"""post_review.py 自测：gh 全部打桩，不联网。运行：python3 scripts/review/post_review_selftest.py

标签名、结论、终审标识、增删旗标都从 policy.json 与 post_review 常量取，本文件不写标签字面值（H21 写入点唯一）。
"""
import json
import pathlib
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import post_review as pr_mod  # noqa: E402

REVIEW, LABELS = pr_mod.load_policy()
PASS, FIX = REVIEW["verdicts"]
FINAL = REVIEW["final_reviewer_tag"]
LABEL = LABELS["review_passed"]
SHA = "a" * 40
OTHER = "b" * 40
PR = 42


class Stub:
    """记录每次 gh 调用；按动词返回预设结果。"""

    def __init__(self, head=SHA, view_rc=0, review_rc=0, edit_rc=0, labels=None, labels_rc=0):
        self.calls, self.head, self.view_rc, self.labels_rc = [], head, view_rc, labels_rc
        self.review_rc, self.edit_rc, self.labels = review_rc, edit_rc, labels or []

    def __call__(self, args, stdin=None):
        self.calls.append((list(args), stdin))
        if args[:2] == ["pr", "view"] and "headRefOid" in args:
            return self.view_rc, json.dumps({"headRefOid": self.head}) if self.head is not None else "{}"
        if args[:2] == ["pr", "view"] and "labels" in args:
            return self.labels_rc, json.dumps({"labels": [{"name": n} for n in self.labels]})
        if args[0] == "api":
            return self.review_rc, "{}"
        if args[:2] == ["pr", "edit"]:
            return self.edit_rc, ""
        raise AssertionError(f"意外的 gh 调用：{args}")

    def writes(self):
        return [c for c in self.calls if c[0][0] == "api" or c[0][:2] == ["pr", "edit"]]

    def review_payload(self):
        posts = [c for c in self.calls if c[0][0] == "api"]
        assert len(posts) == 1, posts
        return json.loads(posts[0][1])

    def edits(self):
        return [c[0] for c in self.calls if c[0][:2] == ["pr", "edit"]]


def run(stub, verdict=PASS, reviewer="opus-r1", commit=SHA, text="清单"):
    try:
        return 0, pr_mod.post(PR, commit, verdict, reviewer, text, run=stub)
    except pr_mod.Refused as exc:
        return exc.args[0], exc.args[1]


def case_pass_adds_label():
    s = Stub()
    rc, _ = run(s)
    assert rc == 0
    p = s.review_payload()
    assert p["event"] == "COMMENT" and p["commit_id"] == SHA
    assert s.edits() == [["pr", "edit", str(PR), pr_mod.ADD_FLAG, LABEL]]
    call = [c[0] for c in s.calls if c[0][0] == "api"][0]
    assert f"repos/{{owner}}/{{repo}}/pulls/{PR}/reviews" in call


def case_fix_removes_label():
    s = Stub()
    rc, _ = run(s, verdict=FIX)
    assert rc == 0 and s.review_payload()["commit_id"] == SHA
    assert s.edits() == [["pr", "edit", str(PR), pr_mod.REMOVE_FLAG, LABEL]]


def case_fix_label_absent_ok():
    s = Stub(edit_rc=1, labels=[])
    assert run(s, verdict=FIX)[0] == 0


def case_fix_label_still_there_fails():
    s = Stub(edit_rc=1, labels=[LABEL])
    rc, msg = run(s, verdict=FIX)
    assert rc == pr_mod.EXIT_LABEL_FAILED and "review 已提交" in msg


def case_fix_label_unreadable_fails():
    s = Stub(edit_rc=1, labels_rc=1)
    rc, msg = run(s, verdict=FIX)
    assert rc == pr_mod.EXIT_LABEL_FAILED and "review 已提交" in msg


def case_add_label_fails():
    s = Stub(edit_rc=1)
    rc, msg = run(s)
    assert rc == pr_mod.EXIT_LABEL_FAILED and "review 已提交" in msg
    assert len(s.edits()) == 1


def case_head_changed():
    s = Stub(head=OTHER)
    rc, msg = run(s)
    assert rc == pr_mod.EXIT_REFUSED and "head 已变" in msg
    assert s.writes() == []


def case_head_unreadable():
    for s in (Stub(view_rc=1), Stub(head=None), Stub(head="zz")):
        rc, msg = run(s)
        assert rc == pr_mod.EXIT_REFUSED and "读不到" in msg and s.writes() == [], (msg, s.calls)


def case_review_fails_no_label():
    for verdict in (PASS, FIX):
        s = Stub(review_rc=1)
        rc, _ = run(s, verdict=verdict)
        assert rc == pr_mod.EXIT_REVIEW_FAILED and s.edits() == []


def case_bad_inputs():
    for kw in ({"verdict": "approve"}, {"commit": SHA[:39]}, {"commit": SHA.upper()},
               {"commit": SHA + "a"}, {"commit": "g" * 40}, {"reviewer": " "}):
        s = Stub(head=kw.get("commit", SHA))
        rc, _ = run(s, **kw)
        assert rc == pr_mod.EXIT_REFUSED and s.calls == [], kw


def case_final_review():
    s = Stub()
    assert run(s, reviewer=FINAL)[0] == 0
    assert s.review_payload()["commit_id"] == SHA and s.edits() == []
    s = Stub()
    assert run(s, verdict=FIX, reviewer=FINAL)[0] == 0
    assert s.edits() == [["pr", "edit", str(PR), pr_mod.REMOVE_FLAG, LABEL]]


def case_header_exact():
    s = Stub()
    run(s, verdict=FIX, reviewer="opus-r2", text="第一条\n第二条\n")
    f = REVIEW["header_fields"]
    lines = s.review_payload()["body"].split("\n")
    assert lines[:3] == [f"{f[0]}：{FIX}", f"{f[1]}：{SHA}", f"{f[2]}：opus-r2"], lines
    assert lines[3] == "" and lines[4:] == ["第一条", "第二条", ""], lines


def case_main_cli():
    s = Stub()
    orig, pr_mod.gh = pr_mod.gh, s
    try:
        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as fh:
            fh.write("正文")
        base = ["--pr", str(PR), "--commit", SHA, "--reviewer", "opus-r1", "--body-file"]
        assert pr_mod.main(base + [fh.name, "--verdict", PASS]) == 0
        assert s.review_payload()["body"].endswith("\n\n正文")
        assert pr_mod.main(base + [fh.name + ".none", "--verdict", PASS]) == pr_mod.EXIT_REFUSED
        s.head = OTHER
        assert pr_mod.main(base + [fh.name, "--verdict", PASS]) == pr_mod.EXIT_REFUSED
        assert len([c for c in s.calls if c[0][0] == "api"]) == 1
    finally:
        pr_mod.gh = orig
        pathlib.Path(fh.name).unlink()


def main():
    cases = [v for k, v in globals().items() if k.startswith("case_")]
    failed = 0
    for case in cases:
        try:
            case()
            print(f"通过 {case.__name__}")
        except AssertionError as exc:
            failed += 1
            print(f"失败 {case.__name__}：{exc}")
    print(f"{len(cases) - failed}/{len(cases)} 通过")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
