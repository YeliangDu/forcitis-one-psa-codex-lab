#!/usr/bin/env python3
"""登记一条非代码类用语问题 glossary/issues/<日期>-<HHMMSS>-<会话名>-<短名>.md，然后重新生成 glossary/ISSUES.md。

用法：python3 docs/modernization/glossary/new_term_issue.py --session=<会话名> --scope=domain|kernel|acl --bc=<bc>
        "<词或现象>" "<出处，文件:行或场景>" [--kind=new|dialect|conflict|duplicate|other] [--date=YYYY-MM-DD]
使用范围：domain 新系统某个 bc 的领域用语、kernel 共享内核、acl 只为对接外部系统存在；bc 取 glossary/bcs.yaml 中的 id
（同一用语在不同 bc 含义可以不同，按 bc 登记）。
类型：new 新词、dialect 疑似方言、conflict 同名异义、duplicate 同义异名、other 其他（默认）。
用于文档里的方言、同名异义、同义异名、口头叫法；只登记、不自行裁定，由 one-glossary 处理。
代码与界面标识仍按 CLAUDE.md 第 2 条当场登记提议术语（backend/glossary/proposed/）。
两个参数各为一行，不能含未转义的「|」。
"""
import datetime, os, sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from new_intent import create, parse_args, short_name  # noqa: E402
from glossary.build_issues import KINDS, SCOPES, TEMPLATE, bc_ids  # noqa: E402

if __name__ == "__main__":
    opts, (term, where) = parse_args(__doc__, 2)
    kind = opts.get("kind", "other")
    if "session" not in opts or kind not in KINDS or any("\n" in a or "|" in a or not a.strip() for a in (term, where)):
        sys.exit(__doc__)
    if opts.get("scope") not in SCOPES or opts.get("bc") not in bc_ids():
        sys.exit(f"--scope 须为 {'|'.join(SCOPES)}，--bc 须为 glossary/bcs.yaml 中的 id\n\n{__doc__}")
    create("glossary/issues", opts.get("date", datetime.date.today().isoformat()), opts["session"], short_name(term),
           TEMPLATE.format(term.strip(), where.strip(), opts["scope"], opts["bc"], kind, opts["session"], "待处理"),
           builder="glossary/build_issues.py")
