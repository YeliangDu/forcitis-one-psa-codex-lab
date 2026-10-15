"""用语登记规则（B1，intent 2026-10-15-171649、171959-one-coordinator）：glossary/build_terms.py 对正式词条与提议术语逐条校验，缺项即失败。
规则：
- status 只取 proposed / approved / deprecated；
- scope 必填，只取 domain（领域，某 bc）/ kernel（共享内核）/ acl（防腐层）；bc 必填且在 glossary/bcs.yaml 中；
- kernel：bc 为 kernel，used_by 列出使用它的 bc（错误码除外：通用错误码按 CS-2.24 本来就只在 kernel，供所有 bc 用）；bc 为 kernel 的词条 scope 必须是 kernel；
- acl：acl_class 为 过渡期 / 长期；其余 scope 不写 acl_class；
- upstream_term（下游 bc 自用词对应的上游词条）须是另一个 bc 的已登记词条；
- 指标（kind metric）写口径：formula、grain、as_of；
- 同一 bc 内一个中文名、一个英文名只对应一个词条（弃用的不算）；跨 bc 同名须在 homonyms 里互列对方 id。
存量违规登记在 glossary/term-rules-baseline.json（键 <来源>#<id>:<规则>），只减不增：基线里的问题已修好而没删键也失败。"""
import json, os

STATUSES = ("proposed", "approved", "deprecated")
SCOPES = ("domain", "kernel", "acl")
ACL_CLASSES = ("过渡期", "长期")
METRIC_FIELDS = ("formula", "grain", "as_of")
BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "term-rules-baseline.json")


def _blank(v):
    return v is None or (isinstance(v, str) and not v.strip()) or (isinstance(v, list) and not v)


def check(entries, bcs):
    """entries：[(来源, 词条 dict)]，来源如 terms.yaml、proposed/kernel.yaml；bcs：bc id 集合。返回 {键: 说明}。"""
    problems = {}

    def bad(src, e, rule, msg):
        problems[f"{src}#{e.get('id')}:{rule}"] = msg

    byid = {}
    for src, e in entries:
        byid.setdefault(e.get("id"), []).append((src, e))
    for src, e in entries:
        kind, scope, bc = e.get("kind"), e.get("scope"), e.get("bc")
        if e.get("status") not in STATUSES:
            bad(src, e, "status", f"status「{e.get('status')}」不在 {'/'.join(STATUSES)}")
        if scope not in SCOPES:
            bad(src, e, "scope", f"scope「{scope}」缺失或不在 {'/'.join(SCOPES)}")
        if bc not in bcs:
            bad(src, e, "bc", f"bc「{bc}」不在 glossary/bcs.yaml")
        if (bc == "kernel") != (scope == "kernel") and scope in SCOPES:
            bad(src, e, "kernel", "bc 为 kernel 与 scope 为 kernel 必须同时成立")
        if scope == "kernel" and kind != "error_code":
            used = e.get("used_by")
            if _blank(used) or not isinstance(used, list) or any(u not in bcs or u == "kernel" for u in used):
                bad(src, e, "used_by", "kernel 词条须在 used_by 列出使用它的 bc（取 bcs.yaml 中的 id，不含 kernel）")
        if scope == "acl" and e.get("acl_class") not in ACL_CLASSES:
            bad(src, e, "acl_class", f"acl 词条的 acl_class 须为 {'/'.join(ACL_CLASSES)}")
        if scope in ("domain", "kernel") and "acl_class" in e:
            bad(src, e, "acl_class", "非 acl 词条不写 acl_class")
        up = e.get("upstream_term")
        if up is not None:
            targets = byid.get(up) or []
            if not targets or all(t.get("bc") == bc for _, t in targets):
                bad(src, e, "upstream_term", f"upstream_term「{up}」须是另一个 bc 的已登记词条")
        if kind == "metric":
            missing = [f for f in METRIC_FIELDS if _blank(e.get(f))]
            if missing:
                bad(src, e, "metric", "指标须写口径：缺 " + "、".join(missing))
    live = [(src, e) for src, e in entries if e.get("status") != "deprecated"]
    for field in ("zh", "en"):
        groups = {}
        for src, e in live:
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


def enforce(entries, bcs):
    """校验并对照基线；有新问题或基线过期时 SystemExit。返回（基线中仍存在的问题数, 按规则计数）。"""
    problems = check(entries, bcs)
    baseline = json.load(open(BASELINE, encoding="utf-8")) if os.path.exists(BASELINE) else {"entries": {}}
    allowed = baseline["entries"]
    new = sorted(k for k in problems if k not in allowed)
    stale = sorted(k for k in allowed if k not in problems)
    if new or stale:
        lines = [f"  新问题 {k}：{problems[k]}" for k in new] + [f"  基线已过期（已修好，请从 term-rules-baseline.json 删去）{k}" for k in stale]
        raise SystemExit("用语登记规则校验失败（glossary/term_rules.py）：\n" + "\n".join(lines))
    counts = {}
    for k in problems:
        rule = k.rsplit(":", 1)[1]
        counts[rule] = counts.get(rule, 0) + 1
    return len(problems), counts


def self_test():
    import sys
    bcs = {"kernel", "cost.timesheet", "delivery.execution", "platform.integration"}

    def term(i, **kw):
        e = dict(id=i, zh=i, en=i.title().replace("_", ""), kind="entity", bc="cost.timesheet", scope="domain", status="approved")
        e.update(kw)
        return e

    def rules(*terms):
        return {k.rsplit(":", 1)[1] for k in check([("t", x) for x in terms], bcs)}

    failures = []

    def want(name, got, expected):
        if got != expected:
            failures.append(f"{name}：期望 {sorted(expected)}，得到 {sorted(got)}")

    want("合规", rules(term("a")), set())
    want("status 三值", rules(term("a", status="draft")), {"status"})
    want("deprecated 合法", rules(term("a", status="deprecated")), set())
    want("scope 缺失", rules(term("a", scope=None)), {"scope"})
    want("bc 不在清单", rules(term("a", bc="cost.hours")), {"bc"})
    want("kernel 须有 used_by", rules(term("a", bc="kernel", scope="kernel")), {"used_by"})
    want("kernel used_by 合法", rules(term("a", bc="kernel", scope="kernel", used_by=["cost.timesheet"])), set())
    want("kernel used_by 不含 kernel", rules(term("a", bc="kernel", scope="kernel", used_by=["kernel"])), {"used_by"})
    want("错误码在 kernel 免 used_by", rules(term("a", bc="kernel", scope="kernel", kind="error_code")), set())
    want("bc kernel 而 scope domain", rules(term("a", bc="kernel")), {"kernel"})
    want("acl 须有 acl_class", rules(term("a", scope="acl")), {"acl_class"})
    want("acl 合法", rules(term("a", scope="acl", acl_class="长期")), set())
    want("非 acl 不写 acl_class", rules(term("a", acl_class="长期")), {"acl_class"})
    want("指标口径", rules(term("a", kind="metric", formula="x")), {"metric"})
    want("upstream_term 不存在", rules(term("a", upstream_term="b")), {"upstream_term"})
    want("upstream_term 同 bc", rules(term("a", upstream_term="b"), term("b", zh="乙", en="B")), {"upstream_term"})
    want("upstream_term 合法", rules(term("a", upstream_term="b"), term("b", zh="乙", en="B", bc="delivery.execution")), set())
    want("同 bc 中文重名", rules(term("a", zh="项目"), term("b", zh="项目")), {"unique_zh"})
    want("同 bc 英文重名", rules(term("a", en="Project"), term("b", en="Project")), {"unique_en"})
    want("弃用不算重名", rules(term("a", zh="项目"), term("b", zh="项目", status="deprecated")), set())
    want("跨 bc 同名未互列", rules(term("a", zh="项目"), term("b", zh="项目", bc="delivery.execution")), {"homonym_zh"})
    want("跨 bc 同名已互列", rules(term("a", zh="项目", homonyms=["b"]), term("b", zh="项目", bc="delivery.execution", homonyms=["a"])), set())
    if failures:
        sys.exit("自测失败：\n  " + "\n  ".join(failures))
    print("self-test: ok")


if __name__ == "__main__":
    import sys
    if "--self-test" in sys.argv[1:]:
        self_test()
    else:
        sys.exit("用法：python3 docs/modernization/glossary/term_rules.py --self-test（校验由 glossary/build_terms.py 调用）")
