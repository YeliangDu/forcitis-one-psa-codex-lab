#!/usr/bin/env python3
"""post_review 的前置检查：PR 关联的 Issue 须已排期（定稿 1.8 第 5 点；二乙 #24）。只读：gh pr view 取正文首行与 GitHub 算出的关闭集合，gh issue view 只取状态、标签、标题与正文（标题与正文的形状见 issue_title_problem、issue_body_problem）。
PR 正文首行须恰为「Closes #<卡号>」（与 .github/pull_request_template.md 一致），或「Refs #<卡号>」（卡 #736：只关联不关卡）；本脚本不解析正文其余部分，改问 GitHub 这个 PR 实际会关闭哪些 Issue
（gh pr view --json closingIssuesReferences）：Closes 时集合须恰为首行那一张卡且与本 PR 同仓库（owner、name 比较不分大小写），多一张、少一张、号不一致、跨仓库都拒；
Refs 时集合须为空（正文别处写了 Closes 等于要关卡，须写在首行）。两种写法首行那张卡都须已排期。
已核（one-ops 10-27 只读）：GraphQL 自省 PullRequest 有 closingIssuesReferences；gh 2.88.1 对公开仓库 gh pr view 14815 -R cli/cli 实测每项形如
{id, number, repository:{id, name, owner:{id, login}}, url}，无 state、nameWithOwner。故同仓库只比 owner.login 与 name；number 须整数、二者须字符串，缺或类型不对就拒；url 不作依据。
已排期口径为调度对定稿 1.8 第 5 点的解释（调度 10-27），非用户原话，待用户确认。
已排期 = Issue 是 open，且恰有一个非空的「会话:<名>」标签，且没有「挂起」「hold」标签（精确名）。没有会话标签即待做，多个会话标签归属不明；只认明确已排期，其余一律拒。读不到、gh 出错、输出不是 JSON、字段缺失或类型不对一律拒（失败关闭）。
已知缺口（Q7 收尾，调度 10-27）：①复审结论提交后再改 PR 正文加关联，本检查管不到，合入时的复核属合入队列改造，推后；
②PR 指向非默认分支时 GitHub 的关闭集合可能为空，结果是多拒，不是门坏了。
"""
import json
import os
import re

FIRST_LINE = re.compile(r"(Closes|Refs) #([1-9][0-9]{0,8})[ \t\r]*")
SESSION, HANG, HOLD = "会话:", "挂起", "hold"
TITLE_HEAD = re.compile(r"[a-z]+(\([\w./-]+\))?: (?P<summary>.+)")
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
TEMPLATE = os.path.join(ROOT, ".github", "ISSUE_TEMPLATE", "card.md")   # the one list of sections and of card types
KINDS = re.compile(r"选一：([^。]*)。")   # the card types: the first sentence of the comment under 类型 in the template
COMMENT = re.compile(r"<!--.*?-->", re.S)
SECTION = re.compile(r"^## (.+?)[ \t]*$", re.M)


def fetch(run, args):
    """gh 成功且输出为 JSON 对象时返回该字典，否则 None。"""
    rc, out = run(args)
    try:
        data = json.loads(out) if rc == 0 else None
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def home_repo(run, scope):
    """本 PR 所在仓库的小写 owner/name：有 -R 参数就用它，否则问 gh；取不到返回 None。"""
    slug = scope[1] if scope else (fetch(run, ["repo", "view", "--json", "nameWithOwner"]) or {}).get("nameWithOwner")
    return slug.lower() if isinstance(slug, str) and re.fullmatch(r"[^/\s]+/[^/\s]+", slug) else None


def title_problem(title, body):
    """PR 标题须为「类型(范围): 含中文的摘要（#卡号）」，范围可省，卡号取正文首行 Closes/Refs 那张，括号全角；合格返回 None，否则返回原因并给出该有的样子。
    不替人改标题，也不与卡自己的标题比（卡标题有历史英文）。post_review 前置检查与合入队列合入前各调一次（标题在复审后被改也拦得住）。"""
    link = FIRST_LINE.fullmatch(body.lstrip("﻿").partition("\n")[0]) if isinstance(body, str) else None
    if link is None:
        return "PR 正文首行不是「Closes #<卡号>」或「Refs #<卡号>」，查不了标题里该写的卡号"
    num = link.group(2)
    shape = f"类型(范围): 中文摘要（#{num}）"
    head = TITLE_HEAD.fullmatch(title) if isinstance(title, str) else None
    tail = f"（#{num}）"
    if head is None or not head.group("summary").endswith(tail) or not CJK.search(head.group("summary")[:-len(tail)]):
        return f"PR 标题须为「{shape}」（范围可省，摘要含中文，卡号与正文首行一致，括号全角），现为 {title!r}"
    return None


def issue_title_problem(title):
    """卡（Issue）标题须为「类型(范围): 含中文的摘要」（CS-7.8，与 PR 标题同形、不带（#卡号））；合格返回 None，否则返回原因。标题缺失或不是字符串也拒（失败关闭）。"""
    head = TITLE_HEAD.fullmatch(title) if isinstance(title, str) else None
    if head is None or not CJK.search(head.group("summary")):
        return f"的标题须为「类型(范围): 中文摘要」（范围可省，摘要含中文，冒号后一个空格；CS-7.8），现为 {title!r}"
    return None


def sections(text):
    """{小节名: 去掉 HTML 注释与空白后的内容}，按「## 名」切；重名取先出现的。"""
    text = COMMENT.sub("", text)
    marks = list(SECTION.finditer(text))
    found = {}
    for i, m in enumerate(marks):
        found.setdefault(m.group(1), text[m.end(): marks[i + 1].start() if i + 1 < len(marks) else len(text)].strip())
    return found


def card_kinds(template):
    """卡模板「类型」小节注释里「选一：」后的类型名（如 impl、ops），没有则空表。"""
    m = KINDS.search(template)
    return [item.split()[0] for item in m.group(1).split("/") if item.strip()] if m else []


def issue_body_problem(body):
    """卡正文须含卡模板（.github/ISSUE_TEMPLATE/card.md）的每个小节且都非空，「类型」须是模板里列的类型（与剧本文件名一一对应，由 check_playbooks 守）；合格返回 None，否则返回原因。
    小节表与类型表都现读模板，不另存一份；模板读不到或解析不出小节、类型时拒（失败关闭）。"""
    try:
        template = open(TEMPLATE, encoding="utf-8").read()
    except OSError:
        return "的正文查不了：读不到卡模板（失败关闭）"
    required, kinds = list(sections(template)), card_kinds(template)
    if not required or not kinds:
        return "的正文查不了：卡模板里没有小节或读不出类型表（失败关闭）"
    got = sections(body) if isinstance(body, str) else {}
    empty = [name for name in required if not got.get(name)]
    if empty:
        return f"的正文缺小节或小节为空：{'、'.join(empty)}（须含卡模板的全部小节：{'、'.join(required)}，每节有内容）"
    if got.get("类型") not in kinds:
        return f"的「类型」须恰为剧本类型之一（{' / '.join(kinds)}），现为 {got.get('类型')!r}"
    return None


def closing_mismatch(pr, run, scope, num, refs_only=False):
    """GitHub 算出的关闭集合恰为本仓库的 #num（首行 Refs 时须为空）返回 None，否则返回原因。"""
    refs = (fetch(run, ["pr", "view", str(pr), *scope, "--json", "closingIssuesReferences"]) or {}).get("closingIssuesReferences")
    if not isinstance(refs, list):
        return f"读不到 PR #{pr} 的关闭集合（closingIssuesReferences 缺失或不是列表），拒绝提交（失败关闭）"
    if refs_only:
        return None if not refs else f"首行是 Refs #{num}，GitHub 却算出 PR #{pr} 会关闭 {len(refs)} 个 Issue；要关卡须首行写 Closes，拒绝提交"
    if len(refs) != 1:
        return f"GitHub 算出 PR #{pr} 会关闭 {len(refs)} 个 Issue，须恰为首行那一张卡 #{num}，拒绝提交"
    ref = refs[0] if isinstance(refs[0], dict) else {}
    repo = ref.get("repository") if isinstance(ref.get("repository"), dict) else {}
    number, name = ref.get("number"), repo.get("name")
    login = repo["owner"].get("login") if isinstance(repo.get("owner"), dict) else None
    if not (isinstance(number, int) and not isinstance(number, bool) and isinstance(name, str) and isinstance(login, str)):
        return f"PR #{pr} 的关闭集合项缺 number、repository.name 或 repository.owner.login，拒绝提交（失败关闭）"
    if (home := home_repo(run, scope)) is None:
        return "取不到本 PR 所在仓库，确认不了关闭集合是否同仓库，拒绝提交（失败关闭）"
    if number != int(num) or f"{login}/{name}".lower() != home:
        return f"GitHub 算出的关闭集合与首行 #{num} 不一致或跨仓库，拒绝提交"
    return None


def unscheduled(pr, run, scope):
    """关联 Issue 已排期返回 None，否则返回拒绝原因（不带正文内容）。run 与 post_review.gh 同形，自测传桩。"""
    view = fetch(run, ["pr", "view", str(pr), *scope, "--json", "body,title"]) or {}
    body = view.get("body")
    if not isinstance(body, str):
        return f"读不到 PR #{pr} 的正文，确认不了关联 Issue，拒绝提交（失败关闭）"
    link = FIRST_LINE.fullmatch(body.lstrip("﻿").partition("\n")[0])
    if link is None:
        return "PR 正文首行须恰为「Closes #<卡号>」或「Refs #<卡号>」（取不到、格式不对或跨仓库写法），拒绝提交"
    num = link.group(2)
    if (why := title_problem(view.get("title"), body)) is not None:
        return why + "，拒绝提交"
    if (why := closing_mismatch(pr, run, scope, num, link.group(1) == "Refs")) is not None:
        return why
    issue = fetch(run, ["issue", "view", num, *scope, "--json", "state,labels,title,body"])
    if issue is None:
        return f"读不到 Issue #{num}（gh 不可用、出错或输出不是 JSON 对象），拒绝提交（失败关闭）"
    labels = issue.get("labels")
    names = [x.get("name") if isinstance(x, dict) else None for x in labels] if isinstance(labels, list) else None
    if names is None or not all(isinstance(n, str) for n in names):
        return f"Issue #{num} 的标签读不到或标签名不是字符串，拒绝提交（失败关闭）"
    if issue.get("state") != "OPEN":
        return f"Issue #{num} 不是 open，拒绝提交"
    if {HANG, HOLD} & set(names):
        return f"Issue #{num} 带「{HANG}」或「{HOLD}」标签，未排期，拒绝提交"
    if len([n for n in names if n.startswith(SESSION) and n[len(SESSION):].strip()]) != 1:
        return f"Issue #{num} 没有恰好一个「{SESSION}<名>」标签（待做或归属不明），拒绝提交"
    if (why := issue_title_problem(issue.get("title"))) is not None:
        return f"Issue #{num} {why}，拒绝提交"
    if (why := issue_body_problem(issue.get("body"))) is not None:
        return f"Issue #{num} {why}，拒绝提交"
    return None
