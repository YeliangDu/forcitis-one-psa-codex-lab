"""用语登记规则（B1，intent 2026-10-15-171649、171959-one-coordinator）：glossary/build_terms.py 对正式词条与提议术语逐条校验，缺项即失败。
规则：
- status 只取 proposed / approved / deprecated；弃用条目写 replaced_by（取代它的、未弃用的词条 id）、until（改完的期限）、owner（负责改名的会话）；
- scope 必填，只取 domain（领域，某 bc）/ kernel（共享内核）/ acl（防腐层）；bc 必填，取 glossary/bcs.yaml 中 kind 为 bc 或 kernel 的 id（子域不算）；
- kernel：bc 为 kernel，used_by 列出使用它的 bc（错误码除外：通用错误码按 CS-2.24 本来就只在 kernel，供所有 bc 用）；bc 为 kernel 的词条 scope 必须是 kernel；
- acl：acl_class 为 过渡期 / 长期；其余 scope 不写 acl_class；
- upstream_term（下游 bc 自用词对应的上游词条）须是另一个 bc 的未弃用词条；
- 指标（kind metric）写口径：formula、grain、as_of；
- 同一 bc 内一个中文名、一个英文名只对应一个词条（弃用的不算）；跨 bc 同名须在 homonyms 里互列对方 id，homonyms 只能列真同名的词条；
- 错误码中文名全局唯一（G48、R-5、CS-2.24），不适用互列。
存量违规登记在 glossary/term-rules-baseline.json：entries 为允许的问题（键 <来源>#<id>:<规则>），rules 为各规则的登记条数。
只减不增：出现基线外的问题、某规则的键数与登记条数不等（删键须同步调低 count）、基线里的问题已修好而没删键，都失败。"""
import json, os

STATUSES = ("proposed", "approved", "deprecated")
SCOPES = ("domain", "kernel", "acl")
ACL_CLASSES = ("过渡期", "长期")
METRIC_FIELDS = ("formula", "grain", "as_of")
DEPRECATED_FIELDS = ("replaced_by", "until", "owner")
BC_KINDS = ("bc", "kernel")
BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "term-rules-baseline.json")


def _blank(v):
    return v is None or (isinstance(v, str) and not v.strip()) or (isinstance(v, list) and not v)


def rule_of(key):
    return key.rsplit(":", 1)[1]


def check(entries, bcs):
    """entries：[(来源, 词条 dict)]，来源如 terms.yaml、proposed/kernel.yaml；bcs：bc id → kind（bcs.yaml）。返回 {键: 说明}。"""
    problems = {}

    def bad(src, e, rule, msg):
        problems[f"{src}#{e.get('id')}:{rule}"] = msg

    usable = {b for b, k in bcs.items() if k in BC_KINDS}
    byid = {}
    for src, e in entries:
        byid.setdefault(e.get("id"), []).append((src, e))

    def live_id(i):
        return any(t.get("status") != "deprecated" for _, t in byid.get(i) or [])

    for src, e in entries:
        kind, scope, bc = e.get("kind"), e.get("scope"), e.get("bc")
        if e.get("status") not in STATUSES:
            bad(src, e, "status", f"status「{e.get('status')}」不在 {'/'.join(STATUSES)}")
        if e.get("status") == "deprecated":
            missing = [f for f in DEPRECATED_FIELDS if _blank(e.get(f))]
            if missing or not live_id(e.get("replaced_by")):
                bad(src, e, "deprecated", "弃用条目须写 replaced_by（未弃用的已登记 id）、until、owner：缺或无效 " + "、".join(missing or ["replaced_by"]))
        if scope not in SCOPES:
            bad(src, e, "scope", f"scope「{scope}」缺失或不在 {'/'.join(SCOPES)}")
        if bc not in usable:
            bad(src, e, "bc", f"bc「{bc}」须为 glossary/bcs.yaml 中 kind 为 bc 或 kernel 的 id")
        if (bc == "kernel") != (scope == "kernel") and scope in SCOPES:
            bad(src, e, "kernel", "bc 为 kernel 与 scope 为 kernel 必须同时成立")
        if scope == "kernel" and kind != "error_code":
            used = e.get("used_by")
            if _blank(used) or not isinstance(used, list) or any(u not in usable or u == "kernel" for u in used):
                bad(src, e, "used_by", "kernel 词条须在 used_by 列出使用它的 bc（取 bcs.yaml 中 kind 为 bc 的 id）")
        if scope == "acl" and e.get("acl_class") not in ACL_CLASSES:
            bad(src, e, "acl_class", f"acl 词条的 acl_class 须为 {'/'.join(ACL_CLASSES)}")
        if scope in ("domain", "kernel") and "acl_class" in e:
            bad(src, e, "acl_class", "非 acl 词条不写 acl_class")
        up = e.get("upstream_term")
        if up is not None:
            targets = [t for _, t in byid.get(up) or [] if t.get("status") != "deprecated"]
            if not targets or all(t.get("bc") == bc for t in targets):
                bad(src, e, "upstream_term", f"upstream_term「{up}」须是另一个 bc 的未弃用词条")
        if kind == "metric":
            missing = [f for f in METRIC_FIELDS if _blank(e.get(f))]
            if missing:
                bad(src, e, "metric", "指标须写口径：缺 " + "、".join(missing))
        for h in e.get("homonyms") or []:
            same = [t for _, t in byid.get(h) or [] if t.get("bc") != bc and (t.get("zh") == e.get("zh") or t.get("en") == e.get("en"))]
            if not same:
                bad(src, e, "homonyms", f"homonyms 里的「{h}」须是另一个 bc 中同中文名或同英文名的已登记词条")
    live = [(src, e) for src, e in entries if e.get("status") != "deprecated"]
    codes = {}
    for src, e in live:
        if e.get("kind") == "error_code":
            codes.setdefault(e.get("zh"), []).append((src, e))
    for name, rows in codes.items():
        if len(rows) > 1:
            for src, e in rows:
                others = "、".join(x.get("id") for _, x in rows if x is not e)
                bad(src, e, "unique_zh_error_code", f"错误码中文名「{name}」须全局唯一（通用错误码收进 kernel，CS-2.24）：{others}")
    for field in ("zh", "en"):
        groups = {}
        for src, e in live:
            if e.get("kind") != "error_code" or field == "en":
                groups.setdefault(e.get(field), []).append((src, e))
        for name, rows in groups.items():
            if len(rows) < 2:
                continue
            by_bc = {}
            for src, e in rows:
                by_bc.setdefault(e.get("bc"), []).append((src, e))
            for bc, same in by_bc.items():
                if len(same) > 1:
                    for src, e in same:
                        others = "、".join(x.get("id") for _, x in same if x is not e)
                        bad(src, e, f"unique_{field}", f"同一 bc（{bc}）内{field}「{name}」重复：{others}")
            if len(by_bc) > 1:
                for src, e in rows:
                    want = {x.get("id") for _, x in rows if x.get("bc") != e.get("bc")}
                    have = set(e.get("homonyms") or [])
                    if not want <= have:
                        bad(src, e, f"homonym_{field}", f"跨 bc 同名「{name}」须在 homonyms 互列：缺 " + "、".join(sorted(want - have)))
    return problems


def judge(problems, baseline):
    """对照基线，返回失败说明列表（空表示通过）。"""
    allowed, limits = baseline.get("entries", {}), baseline.get("rules", {})
    out = [f"新问题 {k}：{problems[k]}" for k in sorted(problems) if k not in allowed]
    out += [f"基线已过期（已修好，请从 term-rules-baseline.json 删去）{k}" for k in sorted(allowed) if k not in problems]
    counts = {}
    for k in allowed:
        counts[rule_of(k)] = counts.get(rule_of(k), 0) + 1
    for rule in sorted(set(counts) | set(limits)):
        n, cap = counts.get(rule, 0), (limits.get(rule) or {}).get("count")
        if cap is None or n != cap:
            out.append(f"基线规则 {rule} 有 {n} 条，与登记条数 {cap} 不符：删键时同步调低 count，不得加键")
    return out


def enforce(entries, bcs):
    """校验并对照基线；不通过时 SystemExit。返回（基线中仍存在的问题数, 按规则计数）。"""
    problems = check(entries, bcs)
    baseline = json.load(open(BASELINE, encoding="utf-8"))
    failures = judge(problems, baseline)
    if failures:
        raise SystemExit("用语登记规则校验失败（glossary/term_rules.py）：\n  " + "\n  ".join(failures))
    counts = {}
    for k in problems:
        counts[rule_of(k)] = counts.get(rule_of(k), 0) + 1
    return len(problems), counts


def self_test():
    import sys
    bcs = {"kernel": "kernel", "cost": "subdomain", "cost.timesheet": "bc", "delivery.execution": "bc", "platform.integration": "bc"}

    def term(i, **kw):
        e = dict(id=i, zh=i, en=i.title().replace("_", ""), kind="entity", bc="cost.timesheet", scope="domain", status="approved")
        e.update(kw)
        return e

    def rules(*terms):
        return {rule_of(k) for k in check([("t", x) for x in terms], bcs)}

    failures = []

    def want(name, got, expected):
        if got != expected:
            failures.append(f"{name}：期望 {sorted(expected)}，得到 {sorted(got)}")

    dep = dict(status="deprecated", replaced_by="b", until="R-2a 合并前", owner="one-1a-next-3")
    want("合规", rules(term("a")), set())
    want("status 三值", rules(term("a", status="draft")), {"status"})
    want("弃用须写三字段", rules(term("a", status="deprecated"), term("b", zh="乙", en="B")), {"deprecated"})
    want("弃用合法", rules(term("a", **dep), term("b", zh="乙", en="B")), set())
    want("弃用 replaced_by 不能指向弃用词条", rules(term("a", **dep), term("b", zh="乙", en="B", **dict(dep, replaced_by="a"))), {"deprecated"})
    want("scope 缺失", rules(term("a", scope=None)), {"scope"})
    want("bc 不在清单", rules(term("a", bc="cost.hours")), {"bc"})
    want("子域不能当 bc", rules(term("a", bc="cost")), {"bc"})
    want("kernel 须有 used_by", rules(term("a", bc="kernel", scope="kernel")), {"used_by"})
    want("kernel used_by 合法", rules(term("a", bc="kernel", scope="kernel", used_by=["cost.timesheet"])), set())
    want("kernel used_by 不含 kernel", rules(term("a", bc="kernel", scope="kernel", used_by=["kernel"])), {"used_by"})
    want("kernel used_by 未知 bc", rules(term("a", bc="kernel", scope="kernel", used_by=["cost.hours"])), {"used_by"})
    want("错误码在 kernel 免 used_by", rules(term("a", bc="kernel", scope="kernel", kind="error_code")), set())
    want("bc kernel 而 scope domain", rules(term("a", bc="kernel")), {"kernel"})
    want("bc kernel 而 scope acl", rules(term("a", bc="kernel", scope="acl", acl_class="长期")), {"kernel"})
    want("acl 须有 acl_class", rules(term("a", scope="acl")), {"acl_class"})
    want("acl 合法", rules(term("a", scope="acl", acl_class="长期")), set())
    want("非 acl 不写 acl_class", rules(term("a", acl_class="长期")), {"acl_class"})
    want("指标口径", rules(term("a", kind="metric", formula="x")), {"metric"})
    want("upstream_term 不存在", rules(term("a", upstream_term="b")), {"upstream_term"})
    want("upstream_term 同 bc", rules(term("a", upstream_term="b"), term("b", zh="乙", en="B")), {"upstream_term"})
    want("upstream_term 指向弃用", rules(term("a", upstream_term="b"), term("b", zh="乙", en="B", bc="delivery.execution", **dict(dep, replaced_by="a"))), {"upstream_term"})
    want("upstream_term 合法", rules(term("a", upstream_term="b"), term("b", zh="乙", en="B", bc="delivery.execution")), set())
    want("同 bc 中文重名", rules(term("a", zh="项目"), term("b", zh="项目")), {"unique_zh"})
    want("同 bc 英文重名", rules(term("a", en="Project"), term("b", en="Project")), {"unique_en"})
    want("弃用不算重名", rules(term("a", zh="项目"), term("b", zh="项目", **dict(dep, replaced_by="a"))), set())
    want("跨 bc 中文同名未互列", rules(term("a", zh="项目"), term("b", zh="项目", bc="delivery.execution")), {"homonym_zh"})
    want("跨 bc 英文同名未互列", rules(term("a", en="Party"), term("b", en="Party", bc="delivery.execution")), {"homonym_en"})
    want("跨 bc 同名已互列", rules(term("a", zh="项目", homonyms=["b"]), term("b", zh="项目", bc="delivery.execution", homonyms=["a"])), set())
    want("homonyms 列了不同名的词条", rules(term("a", homonyms=["b"]), term("b", zh="乙", en="B", bc="delivery.execution")), {"homonyms"})
    want("错误码中文名全局唯一、互列也不行", rules(term("a", kind="error_code", zh="未登录", homonyms=["b"]),
                                       term("b", kind="error_code", zh="未登录", bc="delivery.execution", homonyms=["a"])), {"unique_zh_error_code"})
    problems = {"t#a:scope": "x"}
    base = {"entries": {"t#a:scope": "x"}, "rules": {"scope": {"count": 1}}}
    want("基线放行", set(judge(problems, base)), set())
    want("基线外新问题", len(judge({"t#a:scope": "x", "t#b:scope": "y"}, base)) == 1, True)
    want("基线过期", len(judge({}, base)) == 1, True)
    want("基线加键超上限", len(judge({"t#a:scope": "x", "t#b:scope": "y"}, {"entries": {"t#a:scope": "x", "t#b:scope": "y"}, "rules": {"scope": {"count": 1}}})) == 1, True)
    want("删键而没调低 count", len(judge({"t#a:scope": "x"}, {"entries": {"t#a:scope": "x"}, "rules": {"scope": {"count": 2}}})) == 1, True)
    want("规则键全删而 count 未归零", len(judge({}, {"entries": {}, "rules": {"scope": {"count": 1}}})) == 1, True)
    want("规则清零", judge({}, {"entries": {}, "rules": {"scope": {"count": 0}}}), [])
    want("基线规则未登记上限", len(judge(problems, {"entries": {"t#a:scope": "x"}, "rules": {}})) == 1, True)
    if failures:
        sys.exit("自测失败：\n  " + "\n  ".join(failures))
    print("self-test: ok")


if __name__ == "__main__":
    import sys
    if "--self-test" in sys.argv[1:]:
        self_test()
    else:
        sys.exit("用法：python3 docs/modernization/glossary/term_rules.py --self-test（校验由 glossary/build_terms.py 调用）")
