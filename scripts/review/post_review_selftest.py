#!/usr/bin/env python3
"""post_review.py 自测：gh 全部打桩，不联网。运行：python3 scripts/review/post_review_selftest.py

标签名从 policy.json 取，结论与类别从 post_review 常量取，本文件不写标签字面值（H21 写入点唯一）。
"""
import json
import os
import pathlib
import sys
import tempfile
from unittest import mock

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import issue_gate  # noqa: E402
import post_review as pr_mod  # noqa: E402

REVIEW, LABELS = pr_mod.load_policy()
F = REVIEW["header_fields"]
PASS, FIX, FIRST, FINAL = pr_mod.PASS, pr_mod.FIX, pr_mod.FIRST, pr_mod.FINAL
LABEL = LABELS["review_passed"]
SHA, OTHER, PR = "a" * 40, "b" * 40, 42
R1, R2 = f"opus-{FIRST}-r1", f"opus-{FINAL}-a1"
ISSUE, SESSION_LABEL = 7, "会话:one-ops"
BODY = f"Closes #{ISSUE}\n\n## 改动说明\n"
HOME = "Owner/Repo"   # 桩里本 PR 所在仓库；大小写混写，核对不分大小写


def closing(number, slug):
    """gh pr view --json closingIssuesReferences 的一项，形状取自 one-ops 10-27 在公开仓库的只读实测（无 state、无 nameWithOwner）。"""
    owner, name = slug.split("/")
    return {"id": "I_x", "number": number, "repository": {"id": "R_x", "name": name, "owner": {"id": "O_x", "login": owner}},
            "url": f"https://github.com/{slug}/issues/{number}"}


def header(verdict=PASS, commit=SHA, reviewer=R1, colon="："):
    return f"{F[0]}{colon}{verdict}\n{F[1]}{colon}{commit}\n{F[2]}{colon}{reviewer}\n\n正文"


def rv(kind, verdict, commit=SHA, commit_id=None):
    return {"body": header(verdict, commit, f"opus-{kind}-x"), "commit_id": commit_id or commit}


class Stub:
    """记录每次 gh 调用；按动词返回预设结果。heads 依次给出每次读到的 head，最后一个重复。"""

    def __init__(self, heads=(SHA,), view_rc=0, review_rc=0, post_out=None, edit_rc=0,
                 labels=(), labels_rc=0, labels_out=None, reviews=(), reviews_rc=0, reviews_out=None,
                 body=BODY, body_rc=0, body_out=None, issue_state="OPEN", issue_labels=(SESSION_LABEL,), issue_rc=0, issue_out=None,
                 refs=None, refs_rc=0, refs_out=None, home_out=None):
        self.body, self.body_rc, self.body_out = body, body_rc, body_out   # 关联 Issue 检查（#24）：PR 正文、GitHub 算的关闭集合与 Issue 的桩；缺省为已排期
        self.refs, self.refs_rc, self.refs_out, self.home_out = refs, refs_rc, refs_out, home_out
        self.issue_state, self.issue_labels, self.issue_rc, self.issue_out = issue_state, issue_labels, issue_rc, issue_out
        self.calls, self.heads, self.view_rc, self.review_rc, self.post_out = [], list(heads), view_rc, review_rc, post_out
        self.edit_rc, self.labels, self.labels_rc, self.labels_out = edit_rc, labels, labels_rc, labels_out
        self.reviews, self.reviews_rc, self.reviews_out = list(reviews), reviews_rc, reviews_out

    def __call__(self, args, stdin=None):
        self.calls.append((list(args), stdin))
        if args[:2] == ["pr", "view"] and "headRefOid" in args:
            head = self.heads.pop(0) if len(self.heads) > 1 else self.heads[0]
            return self.view_rc, json.dumps({"headRefOid": head}) if head is not None else "{}"
        slug = args[args.index("-R") + 1] if "-R" in args else HOME
        if args[:2] == ["repo", "view"]:
            return 0, json.dumps({"nameWithOwner": HOME}) if self.home_out is None else self.home_out
        if args[:2] == ["pr", "view"] and "closingIssuesReferences" in args:
            refs = [closing(ISSUE, slug)] if self.refs is None else self.refs
            return self.refs_rc, json.dumps({"closingIssuesReferences": refs}) if self.refs_out is None else self.refs_out
        if args[:2] == ["pr", "view"] and "body" in args:
            return self.body_rc, json.dumps({"body": self.body}) if self.body_out is None else self.body_out
        if args[:2] == ["issue", "view"]:
            out = json.dumps({"state": self.issue_state, "labels": [{"name": n} for n in self.issue_labels]})
            return self.issue_rc, out if self.issue_out is None else self.issue_out
        if args[:2] == ["pr", "view"] and "labels" in args:
            out = json.dumps({"labels": [{"name": n} for n in self.labels]})
            return self.labels_rc, out if self.labels_out is None else self.labels_out
        if args[:3] == ["api", "--method", "POST"]:
            out = json.dumps({"commit_id": json.loads(stdin)["commit_id"]})
            return self.review_rc, out if self.post_out is None else self.post_out
        if args[:3] == ["api", "--paginate", "--slurp"]:
            return self.reviews_rc, json.dumps([self.reviews]) if self.reviews_out is None else self.reviews_out
        if args[:2] == ["pr", "edit"]:
            return self.edit_rc, ""
        raise AssertionError(f"意外的 gh 调用：{args}")

    def writes(self):
        return [c for c in self.calls if c[0][:3] == ["api", "--method", "POST"] or c[0][:2] == ["pr", "edit"]]

    def review_payload(self):
        posts = [c for c in self.calls if c[0][:3] == ["api", "--method", "POST"]]
        assert len(posts) == 1, posts
        return json.loads(posts[0][1])

    def edits(self):
        return [c[0] for c in self.calls if c[0][:2] == ["pr", "edit"]]


def run(stub, verdict=PASS, reviewer=R1, commit=SHA, text=None, pr=PR, **kw):
    """text 缺省为由 verdict/commit/reviewer 拼出的正文文件全文。"""
    text = header(verdict, commit, reviewer) if text is None else text
    try:
        return 0, pr_mod.post(pr, text, run=stub, **kw)
    except pr_mod.Refused as exc:
        return exc.args[0], exc.args[1]


def edit(flag):
    return [["pr", "edit", str(PR), flag, LABEL]]


def case_pass_adds_label():
    s = Stub()
    rc, _ = run(s)
    p = s.review_payload()
    assert rc == 0 and p["event"] == "COMMENT" and p["commit_id"] == SHA
    assert s.edits() == edit(pr_mod.ADD_FLAG)
    assert f"repos/{{owner}}/{{repo}}/pulls/{PR}/reviews" in s.writes()[0][0]
    assert len([c for c in s.calls if "headRefOid" in c[0]]) == 2   # 提交前与加标签前各读一次 head


def case_fix_removes_label():
    for reviewer in (R1, R2):
        for guard in (False, True):
            s = Stub()
            assert run(s, verdict=FIX, reviewer=reviewer, guard=guard)[0] == 0
            assert s.review_payload()["commit_id"] == SHA and s.edits() == edit(pr_mod.REMOVE_FLAG)


def case_fix_label_absent_ok():
    assert run(Stub(edit_rc=1, labels=[]), verdict=FIX)[0] == 0


def case_fix_label_recheck_fails_closed():
    bad = [None, "{}", '{"labels": null}', "[]", '{"labels": [1]}', '{"labels": [{"x": 1}]}', "坏 JSON"]
    for out in bad:
        s = Stub(edit_rc=1, labels=[LABEL], labels_out=out)
        rc, msg = run(s, verdict=FIX)
        assert rc == pr_mod.EXIT_LABEL_FAILED and "review 已提交" in msg, (out, rc, msg)
    rc, msg = run(Stub(edit_rc=1, labels_rc=1), verdict=FIX)
    assert rc == pr_mod.EXIT_LABEL_FAILED and "review 已提交" in msg


def case_add_label_fails():
    s = Stub(edit_rc=1)
    rc, msg = run(s)
    assert rc == pr_mod.EXIT_LABEL_FAILED and "review 已提交" in msg and len(s.edits()) == 1


def case_head_changed_before_post():
    s = Stub(heads=(OTHER,))
    rc, msg = run(s)
    assert rc == pr_mod.EXIT_REFUSED and "head 已变" in msg and s.writes() == []
    for s in (Stub(view_rc=1), Stub(heads=(None,)), Stub(heads=("zz",))):
        rc, msg = run(s)
        assert rc == pr_mod.EXIT_REFUSED and "读不到" in msg and s.writes() == [], (msg, s.calls)


def case_head_changed_before_label():
    for s in (Stub(heads=(SHA, OTHER)), Stub(heads=(SHA, None)), Stub(post_out=json.dumps({"commit_id": OTHER})),
              Stub(heads=(SHA, OTHER), reviews=[rv(FIRST, PASS)])):
        guard = bool(s.reviews)
        rc, msg = run(s, reviewer=R2 if guard else R1, guard=guard)
        assert rc == pr_mod.EXIT_LABEL_FAILED and "review 已提交" in msg and s.edits() == [], (rc, msg)
    s = Stub(post_out="非 JSON")   # 返回里没有 commit_id：只靠再读 head
    assert run(s)[0] == 0 and s.edits() == edit(pr_mod.ADD_FLAG)


def case_review_fails_no_label():
    for verdict in (PASS, FIX):
        s = Stub(review_rc=1)
        assert run(s, verdict=verdict)[0] == pr_mod.EXIT_REVIEW_FAILED and s.edits() == []


def case_bad_inputs():
    bad = [{"verdict": "approve"}, {"verdict": f"{PASS}。"}, {"commit": SHA[:39]}, {"commit": SHA.upper()},
           {"commit": SHA + "a"}, {"commit": "g" * 40}, {"pr": 0}, {"pr": -1}, {"repo": "o"}, {"repo": "o/r;x"}]
    bad += [{"reviewer": r} for r in (" ", "opus-r1", f"opus-{FINAL}", f"opus-{FIRST}-", f"opus-{FIRST}-a 1",
            f"opus-{FIRST}-a\n{F[0]}：{PASS}", f"opus-{FIRST}-a\tb", f"opus- {FIRST}-a", f"opus-{FIRST}x-a",
            f"opus-x-{FIRST}-a", f"-{FIRST}-a", f"op us-{FIRST}-a", f"opus-{FIRST}-a:b", f"opus-{FIRST}-a：b",
            f"opus-{FIRST}-a\x07", f"opus-{FIRST}-a\u3000b", f"opus-{FIRST}{FINAL}-a")]
    bad += [{"text": t} for t in ("", "正文", header().replace("\n\n", "\n", 1), header().replace("\n", "\n\n", 1),
                                 f"{F[0]}：{PASS}\n{F[1]}：{SHA}")]
    for kw in bad:
        s = Stub()
        rc, _ = run(s, **kw)
        assert rc == pr_mod.EXIT_REFUSED and s.calls == [], kw
    s = Stub()   # policy 字段名带换行：生成的机读头过不了 parse_header，不提交
    rc, _ = run(s, policy=({**REVIEW, "header_fields": [F[0] + "\n", F[1], F[2]]}, LABELS))
    assert rc == pr_mod.EXIT_REFUSED and s.writes() == []


def case_reviewer_kind():
    s = Stub()
    assert run(s, reviewer=R2)[0] == 0 and s.edits() == []          # 终审通过只记录
    s = Stub()
    assert run(s, reviewer=f"opus-{FIRST}-a-b.1")[0] == 0 and s.edits() == edit(pr_mod.ADD_FLAG)


def case_verdicts_order_swapped():
    policy = ({**REVIEW, "verdicts": [FIX, PASS]}, LABELS)
    s = Stub()
    assert run(s, verdict=FIX, policy=policy)[0] == 0 and s.edits() == edit(pr_mod.REMOVE_FLAG)
    s = Stub()
    assert run(s, verdict=PASS, policy=policy)[0] == 0 and s.edits() == edit(pr_mod.ADD_FLAG)


def case_parse_header():
    ok = {"verdict": PASS, "commit": SHA, "reviewer": R2, "kind": FINAL}
    assert pr_mod.parse_header(header(reviewer=R2), F) == ok
    assert pr_mod.parse_header(header(reviewer=R2, colon=":"), F) == ok
    assert pr_mod.parse_header(f"  {F[0]}：{PASS} \n{F[1]}: {SHA}\u3000\n{F[2]}：{R2}", F) == ok
    assert pr_mod.parse_header(header(reviewer=R2) + f"\n{F[0]}：{FIX}", F) == ok   # 第 4 行起是正文
    bad = [header(reviewer=f"opus-{FIRST}-a\tb"), f"{F[0]}：\t{PASS}\n{F[1]}：{SHA}\n{F[2]}：{R1}", header(verdict=f"{PASS}：x"),
           header(verdict=f"{PASS} {FIX}"), header(commit=SHA.upper()), header(reviewer="opus-复审-a"),
           header(reviewer=f"opus-{FIRST}{FINAL}-a"), header(reviewer=f"opus-{FIRST}-a\x0bb"), header(reviewer="opus-r1"),
           f"{F[1]}：{SHA}\n{F[0]}：{PASS}\n{F[2]}：{R1}", f"{F[0]}：{PASS}\n{F[1]}：{SHA}", header(verdict="approve"),
           f"{F[0]}：{PASS}\n\n{F[1]}：{SHA}\n{F[2]}：{R1}", None, 7,
           *(header().replace("\n", c + "\n", 1) for c in ("\r", "\x85", "\u2028", "\x0b", "\x0c", "\x1c"))]   # a line ending other than \n is invalid, not stripped
    for text in bad:
        assert pr_mod.parse_header(text, F) is None, repr(text)


def case_guard():
    def go(reviewer, reviews, **kw):
        s = Stub(reviews=reviews, **kw)
        rc, msg = run(s, reviewer=reviewer, guard=True)
        return rc, msg, s.edits()
    rc, msg, ed = go(R1, [rv(FIRST, PASS)])                                   # 只有首轮
    assert rc == 0 and ed == [] and "等另一份结论" in msg
    assert go(R2, [rv(FINAL, PASS)])[2] == []                                  # 只有终审
    assert go(R2, [rv(FIRST, PASS), rv(FINAL, PASS)])[2] == edit(pr_mod.ADD_FLAG)  # 两份都通
    assert go(R1, [rv(FINAL, PASS)])[2] == edit(pr_mod.ADD_FLAG)
    assert go(R2, [rv(FIRST, PASS), rv(FIRST, FIX)])[2] == []                 # 首轮最新为必改
    assert go(R1, [rv(FINAL, PASS), rv(FINAL, FIX)])[2] == []                 # 终审最新为必改
    assert go(R2, [rv(FIRST, PASS, commit=OTHER, commit_id=OTHER)])[2] == []  # 别的 commit
    assert go(R2, [rv(FIRST, PASS, commit_id=OTHER)])[2] == []                # review 挂在别的 commit
    assert go(R2, [rv(FIRST, PASS, commit=OTHER, commit_id=SHA)])[2] == []    # 机读头写的是别的 commit
    assert go(R2, [{"body": None}, 3, rv(FIRST, PASS).get("x", {})])[2] == []
    for kw in ({"reviews_rc": 1}, {"reviews_out": "坏"}, {"reviews_out": "{}"}, {"reviews_out": "[{}]"}):
        rc, msg, ed = go(R2, [rv(FIRST, PASS)], **kw)
        assert rc == 4 and ed == [] and "请重跑" in msg, kw   # unreadable is not "the other one has not come yet"


def case_repo_passthrough():
    s = Stub()
    assert run(s, repo="o/r")[0] == 0
    assert all(c[0][3:5] == ["-R", "o/r"] for c in s.calls if c[0][0] == "pr"), s.calls
    assert s.writes()[0][0][3] == f"repos/o/r/pulls/{PR}/reviews"


def case_policy_and_gh_errors():
    with tempfile.TemporaryDirectory() as d:
        p = pathlib.Path(d) / "policy.json"
        for content in ("坏", "{}", json.dumps({"review": REVIEW}), json.dumps({"review": {**REVIEW, "verdicts": [PASS, "x"]},
                        "labels": LABELS}), json.dumps({"review": {**REVIEW, "header_fields": F[:2]}, "labels": LABELS})):
            p.write_text(content, encoding="utf-8")
            try:
                pr_mod.load_policy(p)
                raise AssertionError(content)
            except pr_mod.Refused as exc:
                assert exc.args[0] == pr_mod.EXIT_REFUSED
        p.write_text(header(), encoding="utf-8")
        with mock.patch.dict(os.environ, {"PATH": d}):
            assert pr_mod.gh(["--version"])[0] == 127
            assert pr_mod.main(["--pr", "1", "--body-file", str(p)]) == pr_mod.EXIT_REFUSED
    with mock.patch.object(pr_mod.subprocess, "run") as sp:
        sp.return_value = mock.Mock(returncode=0, stdout="", stderr="")
        pr_mod.gh(["x"])
        assert sp.call_args.kwargs.get("encoding") == "utf-8"


def case_body_verbatim():
    """正文文件原样作为 review 正文；结论、commit、复审者只从文件头解析。"""
    for text in (header(PASS) + "\n第二条\n", f" {F[0]}: {PASS}\n{F[1]}:{SHA}\n{F[2]}：{R1}\n"):
        s = Stub()
        assert run(s, text=text)[0] == 0
        p = s.review_payload()
        assert p["body"] == text and p["commit_id"] == SHA and s.edits() == edit(pr_mod.ADD_FLAG)
    s = Stub()
    assert run(s, text=header(FIX, reviewer=R2))[0] == 0 and s.edits() == edit(pr_mod.REMOVE_FLAG)


def refused(s, **kw):
    rc, msg = run(s, **kw)
    assert rc == pr_mod.EXIT_REFUSED and s.writes() == [], (rc, msg, s.calls)
    return msg


def case_issue_scheduled_passes():
    s = Stub()
    assert run(s, repo="o/r")[0] == 0 and s.edits()[0][-2:] == [pr_mod.ADD_FLAG, LABEL]
    issue_calls = [c[0] for c in s.calls if c[0][0] == "issue"]
    assert issue_calls == [["issue", "view", str(ISSUE), "-R", "o/r", "--json", "state,labels"]], issue_calls   # 只读，不取正文
    s = Stub(issue_labels=(SESSION_LABEL, "enhancement"))   # 其他标签不碍事
    assert run(s)[0] == 0
    for verdict, reviewer in ((FIX, R1), (PASS, R2)):
        assert run(Stub(), verdict=verdict, reviewer=reviewer)[0] == 0


def case_issue_pending_or_hung_refused():
    for labels in ((), ("enhancement",), (SESSION_LABEL, "挂起"), (SESSION_LABEL, "hold"), ("挂起",), ("hold",), (SESSION_LABEL, "挂起", "hold"),
                   (SESSION_LABEL, "会话:one-dev"), ("会话:",), ("会话: ",)):
        for reviewer, guard in ((R1, False), (R2, False), (R1, True), (R2, True)):   # 有放行意义的结论：首轮通过、终审通过
            msg = refused(Stub(issue_labels=labels), reviewer=reviewer, guard=guard)
            assert "拒绝提交" in msg and f"#{ISSUE}" in msg, (labels, msg)
    assert "挂起" in refused(Stub(issue_labels=(SESSION_LABEL, "挂起")))
    assert "待做" in refused(Stub(issue_labels=()))
    assert "不是 open" in refused(Stub(issue_state="CLOSED"))
    assert "不是 open" in refused(Stub(issue_state=None))


def case_issue_not_scheduled_fix_still_posts():
    """必改不拦、摘「复审通过」标签也不拦（含 --guard、终审、Issue 读不到或 closed）。"""
    for kw in ({"issue_labels": (SESSION_LABEL, "挂起")}, {"issue_labels": (SESSION_LABEL, "hold")}, {"issue_labels": ()},
               {"issue_state": "CLOSED"}, {"issue_rc": 1}, {"body": "无关联"}, {"body_rc": 1}):
        for reviewer in (R1, R2):
            for guard in (False, True):
                s = Stub(**kw)
                assert run(s, verdict=FIX, reviewer=reviewer, guard=guard)[0] == 0, (kw, reviewer, guard)
                assert s.review_payload()["commit_id"] == SHA and s.edits() == edit(pr_mod.REMOVE_FLAG), kw
                assert [c for c in s.calls if c[0][0] == "issue"] == [], kw   # 必改不去查 Issue
    s = Stub(issue_labels=(SESSION_LABEL, "挂起"))   # 对照：同一桩下首轮通过被拒
    assert "挂起" in refused(s)


def case_issue_unreadable_fails_closed():
    bad_body = [{"body_rc": 1}, {"body_out": "坏 JSON"}, {"body_out": "[]"}, {"body_out": "{}"}, {"body_out": '{"body": null}'},
                {"body_out": '{"body": 7}'}, {"body": None}]
    bad_issue = [{"issue_rc": 1}, {"issue_out": "坏 JSON"}, {"issue_out": "[]"}, {"issue_out": "{}"}, {"issue_out": '{"state": "OPEN"}'},
                 {"issue_out": '{"state": "OPEN", "labels": null}'}, {"issue_out": '{"state": "OPEN", "labels": {"name": "x"}}'},
                 {"issue_out": '{"state": "OPEN", "labels": [1]}'}, {"issue_out": '{"state": "OPEN", "labels": [{"x": 1}]}'},
                 {"issue_out": json.dumps({"state": "OPEN", "labels": [{"name": SESSION_LABEL}, {"name": 5}]})},
                 {"issue_out": json.dumps({"labels": [{"name": SESSION_LABEL}]})}]
    for kw in bad_body + bad_issue:
        msg = refused(Stub(**kw))
        assert "失败关闭" in msg or "不是 open" in msg or "格式" in msg, (kw, msg)
    for out in ((127, ""), (1, "{}"), (124, '{"state": "OPEN", "labels": [{"name": "会话:x"}]}')):   # gh 不存在、非零退出码（即使输出看似合格）
        assert issue_gate.unscheduled(PR, Stub(issue_rc=out[0], issue_out=out[1]), ["-R", "o/r"]) is not None
    assert issue_gate.unscheduled(PR, Stub(), ["-R", "o/r"]) is None   # 对照：同一桩正常时放行
    assert issue_gate.unscheduled(PR, lambda args: (127, ""), []) is not None


def case_issue_link_forms():
    ok = [BODY, f"Closes #{ISSUE}", f"Closes #{ISSUE}\r\n\r\n正文\r\n", f"﻿Closes #{ISSUE}\n", f"Closes #{ISSUE}  \n正文",
          f"Closes #{ISSUE}\n<!-- 写 Closes #9 -->\n正文", f"Closes #{ISSUE}\n<!--\nFixes #9\n-->", f"Closes #{ISSUE}\n见 #9、PR #10\n"]
    for body in ok:
        s = Stub(body=body)
        assert run(s)[0] == 0 and [c[0][2] for c in s.calls if c[0][0] == "issue"] == [str(ISSUE)], repr(body)
    bad = ["", "无关联", "\n" + BODY, " " + BODY, "closes #7", "CLOSES #7", "Closes  #7", "Closes #07", "Closes #0", "Closes #", "Closes #<卡>",
           "Fixes #7", "Closes #7 Closes #8", "Closes #7, #8", "Closes #7 #8", "Closes #7.", "Closes #1234567890", "Closes #-1",
           "<!-- c -->\nCloses #7"]
    for body in bad:
        s = Stub(body=body)
        msg = refused(s)
        assert [c for c in s.calls if c[0][0] == "issue"] == [] and "拒绝提交" in msg, (body, msg)   # 关联不明就不去查 Issue


def case_issue_multi_link_forms():
    """首行跨仓库写法一律拒（不查 Issue）；正文其余部分不再自己解析，关闭集合以 GitHub 的 closingIssuesReferences 为准。"""
    url = "https://github.com/o/r/issues/8"
    for body in (f"Closes o/r#{ISSUE}", f"Closes https://github.com/o/r/issues/{ISSUE}", f"Closes o/r#{ISSUE}\n正文", f"Closes {url}"):
        s = Stub(body=body)
        msg = refused(s)
        assert [c for c in s.calls if c[0][0] == "issue"] == [] and "拒绝提交" in msg, (body, msg)


def case_issue_closing_number_bool():
    """True == 1：首行 Closes #1、gh 返回 number 为 true（布尔）时，若只比数值会放行，必须拒（上一条 ISSUE=7 的布尔用例拒不出区别）。"""
    s = Stub(body="Closes #1", refs=[closing(True, HOME)])
    msg = refused(s)
    assert "失败关闭" in msg and [c for c in s.calls if c[0][0] == "issue"] == [], msg
    assert run(Stub(body="Closes #1", refs=[closing(1, HOME)]))[0] == 0   # 对照：整数 1 放行


def case_issue_closing_set():
    """关闭集合须恰为首行那一张同仓库的卡；多、少、不一致、跨仓库、结构不对、读不到都拒，且不去查 Issue（#24 方案 C）。"""
    ok, other = closing(ISSUE, HOME), closing(8, HOME)
    s = Stub(refs=[ok])   # 恰为首行卡，放行；仓库名大小写不同也认
    assert run(s)[0] == 0 and [c[0][:3] for c in s.calls if c[0][0] == "issue"] == [["issue", "view", str(ISSUE)]]
    assert run(Stub(refs=[closing(ISSUE, HOME.lower())]))[0] == 0 and run(Stub(refs=[closing(ISSUE, HOME.upper())]))[0] == 0
    assert any("closingIssuesReferences" in c[0] and c[0][:2] == ["pr", "view"] for c in s.calls)   # 只读 GitHub 算的集合
    bad = {"多一张": [ok, other], "多一张（同号重复）": [ok, ok], "为空": [], "号不一致": [other], "跨仓库": [closing(ISSUE, "o/r")],
           "跨 owner": [closing(ISSUE, "x/Repo")], "跨 name": [closing(ISSUE, "Owner/x")],
           "缺 number": [{"repository": ok["repository"]}], "number 非整数": [{**ok, "number": str(ISSUE)}], "number 是布尔": [{**ok, "number": True}],
           "缺 repository": [{"number": ISSUE}], "repository 非字典": [{"number": ISSUE, "repository": "o/r"}],
           "缺 name": [{"number": ISSUE, "repository": {"owner": {"login": "Owner"}}}], "缺 owner": [{"number": ISSUE, "repository": {"name": "Repo"}}],
           "owner 非字典": [{"number": ISSUE, "repository": {"name": "Repo", "owner": "Owner"}}],
           "缺 login": [{"number": ISSUE, "repository": {"name": "Repo", "owner": {}}}],
           "login 非字符串": [{"number": ISSUE, "repository": {"name": "Repo", "owner": {"login": 1}}}], "项不是字典": ["x"], "项为空": [{}]}
    for why, refs in bad.items():
        s = Stub(refs=refs)
        msg = refused(s)
        assert [c for c in s.calls if c[0][0] == "issue"] == [] and "拒绝提交" in msg, (why, msg)
    for why, kw in {"值不是列表": {"refs": None, "refs_out": '{"closingIssuesReferences": {"number": 7}}'}, "字段缺失": {"refs_out": "{}"},
                    "值为 null": {"refs_out": '{"closingIssuesReferences": null}'}, "JSON 畸形": {"refs_out": "坏 JSON"},
                    "不是对象": {"refs_out": "[]"}, "gh 失败": {"refs_rc": 1}, "gh 失败但输出合格": {"refs_rc": 1, "refs": [ok]},
                    "取不到本仓库": {"home_out": "坏 JSON"}, "本仓库名不合格": {"home_out": '{"nameWithOwner": "x"}'}}.items():
        s = Stub(**kw)
        msg = refused(s)
        assert "失败关闭" in msg and [c for c in s.calls if c[0][0] == "issue"] == [], (why, msg)
    s = Stub(refs=[closing(ISSUE, "o/r")], body=f"Closes #{ISSUE}\n正文")   # -R 指定仓库时不再问 gh 本仓库，直接以它为准
    assert run(s, repo="o/r")[0] == 0 and [c for c in s.calls if c[0][:2] == ["repo", "view"]] == []


def case_issue_refs_first_line():
    """卡 #736：首行 Refs #<卡> 也认（只关联不关卡）：关闭集合须为空、卡须已排期；Closes 照旧须恰为首行卡。"""
    for body in (f"Refs #{ISSUE}", f"Refs #{ISSUE}\n\n## 改动说明\n", f"﻿Refs #{ISSUE}  \r\n正文"):
        s = Stub(body=body, refs=[])
        assert run(s)[0] == 0 and [c[0][2] for c in s.calls if c[0][0] == "issue"] == [str(ISSUE)], repr(body)
    for why, kw, word in (("关闭集合不为空（正文别处写了 Closes）", {"refs": [closing(ISSUE, HOME)]}, "须首行写 Closes"),
                          ("关闭集合是别的卡", {"refs": [closing(8, HOME)]}, "须首行写 Closes"),
                          ("卡未排期（挂起）", {"refs": [], "issue_labels": (SESSION_LABEL, "挂起")}, "挂起"),
                          ("卡没有会话标签", {"refs": [], "issue_labels": ()}, "会话:"),
                          ("卡已关", {"refs": [], "issue_state": "CLOSED"}, "不是 open"),
                          ("读不到关闭集合", {"refs_out": "{}"}, "失败关闭")):
        msg = refused(Stub(body=f"Refs #{ISSUE}", **kw))
        assert word in msg and "拒绝提交" in msg, (why, msg)
    for body in ("refs #7", "Refs  #7", "Refs #07", "Refs #", "Ref #7", "References #7", "Refs #7 Closes #8", "Refs o/r#7"):
        s = Stub(body=body, refs=[])
        assert "拒绝提交" in refused(s) and [c for c in s.calls if c[0][0] == "issue"] == [], body
    assert "须恰为首行那一张卡" in refused(Stub(body=f"Closes #{ISSUE}", refs=[])), "Closes 照旧：关闭集合为空就拒"


def case_prompt_dispatch_rules():
    """卡 #736：复审提示写明开发会话派全新子代理、普通卡 sonnet、权限安全数据范围与守护路径 opus、只跑受影响模块；复核模式已删；机读头仍能被 post_review 解析。"""
    text = (pathlib.Path(pr_mod.__file__).parent / "PROMPT.md").read_text(encoding="utf-8")
    for need in ("由开发会话（作者）自己派", "全新复审子代理", "普通卡用 sonnet", "权限、安全、数据范围类", "review.final_paths", "用 opus", "只跑受影响模块", "调度看差异放行"):
        assert need in text, need
    assert "复核模式" not in text and "逐条复核上一轮必改" not in text, "复核要求已删"
    fence = text.split("```")[1].strip().split("\n")
    assert fence == ["复审结论：通过｜必改", "commit：<40 位 sha>", "复审者：<模型>-<首轮|终审>-<标识>"], fence
    assert pr_mod.parse_header(header(PASS, SHA, "sonnet-首轮-a1")) and pr_mod.parse_header(header(FIX, SHA, "opus-终审-a1")), "sonnet 首轮与 opus 终审的头都能解析"


def case_main_cli():
    s = Stub()
    with mock.patch.object(pr_mod, "gh", s), tempfile.NamedTemporaryFile("w", suffix=".md", encoding="utf-8-sig") as fh:
        fh.write(header())
        fh.flush()
        base = ["--pr", str(PR), "--body-file"]
        assert pr_mod.main(base + [fh.name]) == 0
        assert s.review_payload()["body"] == header()   # 带 BOM 的文件也按原文提交
        assert pr_mod.main(base + [fh.name + ".none"]) == pr_mod.EXIT_REFUSED
        for old in (["--verdict", PASS], ["--commit", SHA], ["--reviewer", R1]):   # 不再接受手填结论
            try:
                pr_mod.main(base + [fh.name] + old)
                raise AssertionError(old)
            except SystemExit as exc:
                assert exc.code == 2, old
        s.heads = [OTHER]
        assert pr_mod.main(base + [fh.name, "--guard", "--repo", "o/r"]) == pr_mod.EXIT_REFUSED
        assert len(s.writes()) == 2   # 只有第一次的 review 与加标签


def main():
    cases = [v for k, v in globals().items() if k.startswith("case_")]
    failed = 0
    for case in cases:
        try:
            case()
            print(f"通过 {case.__name__}")
        except (Exception, SystemExit) as exc:   # noqa: BLE001  自测里任何异常（含 argparse 退出）都算失败
            failed += 1
            print(f"失败 {case.__name__}：{type(exc).__name__} {exc}")
    print(f"{len(cases) - failed}/{len(cases)} 通过")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
