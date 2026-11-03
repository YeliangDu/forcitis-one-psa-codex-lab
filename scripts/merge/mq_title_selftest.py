"""Self-test of the merge queue's title check right before the merge (#1121): a title edited after the review is held, not merged."""
import mq_selftest as t


def run(mq, d):
    gh = t.FakeGh(); gh.add(80)
    result, ev, _, _ = t.once(mq, d, "ttl1", gh, 80)
    assert result == "MERGED" and "HOLD" not in ev, "a well-formed title merges"
    for i, (name, title, body) in enumerate((("English summary", "fix(q): fix the thing（#81）", None), ("no card", "fix(q): 修复", None), ("card differs from the first line", "fix(q): 修复（#99）", None),
                                             ("half-width brackets", "fix(q): 修复(#81)", None), ("first line is not a link", "fix(q): 修复（#81）", "no link"))):
        gh = t.FakeGh(); p = gh.add(81 + i)
        p["title"], p["body"] = title.replace("81", str(81 + i)) if "99" not in title else title, body or p["body"]
        result, ev, log, _ = t.once(mq, d, f"ttl{i + 2}", gh, 81 + i)
        assert result != "MERGED" and not t.merged(gh) and "HOLD" in ev, (name, result, ev)
        assert any("title" in l for l in log.lines if " HOLD " in l), (name, log.lines)
