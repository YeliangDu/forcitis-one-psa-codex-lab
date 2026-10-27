#!/usr/bin/env python3
"""post_review 的前置检查：PR 关联的 Issue 须已排期（定稿 1.8 第 5 点；二乙 #24）。只读：gh pr view 只取正文，gh issue view 只取状态与标签。

关联 Issue 只从 PR 正文首行取，首行须恰为「Closes #<卡号>」（与 .github/pull_request_template.md 一致）；
首行不合格、正文其余部分（HTML 注释除外）另有 Closes／Fixes／Resolves #<号> 的关联（多个），一律拒。
已排期 = Issue 是 open，且恰有一个非空的「会话:<名>」标签，且没有「挂起」「hold」标签（精确名，口径同 scripts/delegate.py 的 fetch_card）。
没有会话标签即待做，多个会话标签归属不明；只认明确已排期，其余一律拒。读不到、gh 出错或超时、输出不是 JSON、标签名不是字符串都拒（失败关闭）。
"""
import json
import re

FIRST_LINE = re.compile(r"Closes #([1-9][0-9]{0,8})[ \t\r]*")
OTHER_LINK = re.compile(r"\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\b[ \t:]*#[0-9]+", re.I)
SESSION, HANG, HOLD = "会话:", "挂起", "hold"


def fetch(run, args):
    """gh 成功且输出为 JSON 对象时返回该字典，否则 None。"""
    rc, out = run(args)
    try:
        data = json.loads(out) if rc == 0 else None
    except (ValueError, TypeError):
        return None
    return data if isinstance(data, dict) else None


def unscheduled(pr, run, scope):
    """关联 Issue 已排期返回 None，否则返回拒绝原因（不带正文内容）。run 与 post_review.gh 同形，自测传桩。"""
    body = (fetch(run, ["pr", "view", str(pr), *scope, "--json", "body"]) or {}).get("body")
    if not isinstance(body, str):
        return f"读不到 PR #{pr} 的正文，确认不了关联 Issue，拒绝提交（失败关闭）"
    first, _, rest = body.lstrip("﻿").partition("\n")
    link = FIRST_LINE.fullmatch(first)
    if link is None:
        return "PR 正文首行须恰为「Closes #<卡号>」（取不到或格式不对），拒绝提交"
    if OTHER_LINK.search(re.sub(r"<!--.*?-->", "", rest, flags=re.S)):
        return "PR 正文里关联了不止一个 Issue（一 PR 一卡），拒绝提交"
    num = link.group(1)
    issue = fetch(run, ["issue", "view", num, *scope, "--json", "state,labels"])
    if issue is None:
        return f"读不到 Issue #{num}（gh 不可用、超时、出错或输出不是 JSON 对象），拒绝提交（失败关闭）"
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
    return None
