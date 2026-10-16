"""build_adrs.py 的自测（build_all.py --check 调用 build_adrs.py --self-test）：每条规则至少一个通过与一个失败用例，旧稿产物逐字节不变。"""
import datetime, os, re


def run(b):
    ctx = dict(bcs={"a.b", "c.d"}, stages={"TA-1", "TA-2"}, catalog=None, today=datetime.date(2026, 10, 20))
    SECS = {"背景": "背景。", "决定": "决定。", "影响面": "影响。", "迁移方案": "迁移。", "回滚方案": "回滚。", "需同步修改的守护规则": "守护。", "后果": "后果。",
            "证据": "证据。", "备选方案（未采用）": "备选。"}

    def mk(no="0100", status="提议", tier="正式", extra=None, secs=None, bc=("a.b",), stage=("TA-1",), category="数据", date="2026-10-16", drop=(), omit=()):
        fields = [("状态", status), ("日期", date), ("档位", tier), ("起草", "one-architecture"), ("类别", category)]
        text = f"# ADR-{no}：标题\n\n"
        multi = {"BC": bc, "阶段": stage}
        rows = dict(fields)
        rows.update(extra or {})
        for name in b.FIELDS:
            if name in drop:
                continue
            if name in b.MULTI:
                items = multi.get(name) if name in multi else (rows.get(name) or [])
                if items:
                    text += f"- **{name}**：\n" + "".join(f"  - {i}\n" for i in items)
            elif name in rows:
                text += f"- **{name}**：{rows[name]}\n"
        body = dict(SECS if secs is None else secs)
        for o in omit:
            body.pop(o, None)
        return text + "\n" + "\n\n".join(f"## {n}\n\n{body[n]}" for n in b.ORDER if n in body) + "\n"

    def adr(text, no=None):
        no = no or re.match(r"# ADR-(\d{4})", text).group(1)
        return b.parse_text(f"src/ADR-{no}.md", text)

    def check(texts, c=None, legacy=()):
        adrs = [adr(t, re.match(r"# ADR-(\d{4})", t).group(1)) for t in texts] + list(legacy)
        return b.validate(adrs, c or ctx)

    def ok(*texts, **kw):
        errors = check(texts, **kw)
        assert not errors, errors

    def bad(fragment, *texts, **kw):
        try:
            errors = check(texts, **kw)
        except ValueError as e:
            errors = [str(e)]
        assert any(fragment in e for e in errors), (fragment, errors)

    quote = {"用户原话": ["「批准」（2026-10-16，intent `x.md`）"], "批准证据": ["intent `x.md`"]}
    quote_only = {"用户原话": ["「搁置」（2026-10-16，intent `x.md`）"]}
    # R-1 状态：每个各一通过与一失败
    ok(mk(status="提议"))
    ok(mk(status="搁置", extra={**quote_only, "理由": "重启条件：岗位重梳完成"}))
    ok(mk(status="已采纳", extra=quote))
    ok(mk(status="已拒绝", extra={**quote_only, "理由": "不要"}))
    ok(mk(status="已撤回", extra={"理由": "并入别处"}))
    ok(mk(status="已废弃", extra={**quote_only, "理由": "不再适用"}))
    ok(mk(status="已被 ADR-0101 取代", extra={}), mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100"]}))
    for wrong in ("已采纳（用户同意）", "采纳", "已被 ADR-101 取代", "Proposed"):
        bad("状态须精确", mk(status=wrong, extra=quote))
    # 旧稿保持前缀匹配并接受新增状态词
    legacy_text = "# ADR-0001：旧\n\n- **状态**：{}\n- **日期**：2026-10-14\n\n" + "\n\n".join(f"## {n}\n\n{SECS[n]}" for n in b.MANDATORY) + "\n"
    for s in ("已采纳（用户同意）", "已拒绝（x）", "已撤回", "搁置：x", "提议"):
        assert not b.validate_legacy(b.parse_text("src/ADR-0001.md", legacy_text.format(s)), {}), s
    assert b.validate_legacy(b.parse_text("src/ADR-0001.md", legacy_text.format("未知")), {})
    # R-2 格式判定与头部解析
    try:
        b.parse_text("src/ADR-0100.md", legacy_text.replace("ADR-0001", "ADR-0100"))
        raise AssertionError("不在 LEGACY_FORMAT 又无档位应失败")
    except ValueError as e:
        assert "LEGACY_FORMAT" in str(e)
    good = mk()
    for name, text in (("未知字段", good.replace("- **档位**", "- **别的**：x\n- **档位**")),
                       ("重复", good.replace("- **档位**：正式\n", "- **档位**：正式\n- **档位**：正式\n")),
                       ("顺序", good.replace("- **日期**：2026-10-16\n- **档位**：正式\n", "- **档位**：正式\n- **日期**：2026-10-16\n")),
                       ("下一级列表", good.replace("- **BC**：\n  - a.b", "- **BC**：a.b")),
                       ("不能为空", good.replace("- **起草**：one-architecture", "- **起草**：")),
                       ("缺少或为空", mk(drop=("阶段",))),
                       ("空行", good.replace("\n\n## 背景", "\n\n\n## 背景"))):
        try:
            b.parse_text("src/ADR-0100.md", text); raise AssertionError(name)
        except ValueError as e:
            assert {"未知字段": "未知", "重复": "重复", "顺序": "顺序", "下一级列表": "下一级列表", "不能为空": "不能为空", "缺少或为空": "缺少或为空", "空行": "空行"}[name] in str(e), (name, e)
    # R-3 条件必填矩阵
    bad("须有用户原话", mk(status="已采纳", extra={"批准证据": ["x"]}))
    bad("须有批准证据", mk(status="已采纳", extra=quote_only))
    bad("重启条件", mk(status="搁置", extra={**quote_only, "理由": "等一等"}))
    bad("须有用户原话", mk(status="搁置", extra={"理由": "重启条件：x"}))
    bad("须有理由", mk(status="已拒绝", extra=quote_only))
    bad("须有用户原话", mk(status="已废弃", extra={"理由": "x"}))
    bad("须有理由", mk(status="已撤回"))
    ok(mk(status="已撤回", extra={"理由": "x"}, secs={**SECS, "背景": "起草会话自行发起。"}))
    bad("属用户指派", mk(status="已撤回", extra={"理由": "x"}, secs={**SECS, "背景": "触发出处：intent 2026-10-16-131918-x.md。"}))
    ok(mk(status="已撤回", extra={"理由": "x", **quote_only}, secs={**SECS, "背景": "触发出处：intent 2026-10-16-131918-x.md。"}))
    bad("不得为「待定」", mk(status="已采纳", extra=quote, category="待定"))
    bad("不得为「待定」", mk(status="已采纳", extra=quote, bc=("待定",)))
    bad("不得为「待定」", mk(status="已采纳", extra=quote, stage=("待定",)))
    ok(mk(status="提议", category="待定", bc=("待定",), stage=("待定",)))
    # R-4 取代与被部分取代
    target = mk(no="0100", status="已采纳", extra={**quote, "被部分取代": ["ADR-0101 决定第 5 条第 4 点"]})
    ok(target, mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 决定第 5 条第 4 点"]}))
    bad("须为「ADR-NNNN」", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 " + "很长" * 16]}), mk(no="0100", status="已采纳", extra=quote))
    for style in ("决定第 12 行", "src/x.md:101", "L138"):
        bad("不得写行号", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 " + style]}), mk(no="0100", status="已采纳", extra=quote))
    bad("取代对象不存在", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0999"]}))
    bad("未标「已被 ADR-0101 取代」", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100"]}), mk(no="0100", status="已采纳", extra=quote))
    bad("未标「已被 ADR-0101 取代」", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 决定 1"]}), mk(no="0100", status="已采纳", extra=quote))
    bad("须为已采纳且「取代」列有 ADR-0100", mk(no="0100", status="已采纳", extra={**quote, "被部分取代": ["ADR-0101 决定 1"]}), mk(no="0101", status="已采纳", extra=quote))
    bad("须为已采纳且「取代」列有 ADR-0100", mk(no="0100", status="已采纳", extra={**quote, "被部分取代": ["ADR-0101 决定 1"]}),
        mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 决定 2"]}) .replace("ADR-0100 决定 2", "ADR-0102 决定 2"), mk(no="0102", status="已采纳", extra=quote))
    # ADR-0049 初稿的取代行（超长说明）必须失败
    bad("须为「ADR-NNNN」", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 的下列条款（只就期满判定与视为同意显示的落点而言）：决定第 5 条第 4 点全文"]}), mk(no="0100", status="已采纳", extra=quote))
    # 旧稿被新稿部分取代：旧稿状态行括注；整份取代：状态以句子开头
    old = lambda s: b.parse_text("src/ADR-0001.md", legacy_text.format(s))
    new_sup = mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0001 决定 1"]})
    ok(new_sup, legacy=[old("已采纳（范围已被 ADR-0101 取代）")])
    bad("未标", new_sup, legacy=[old("已采纳（用户同意）")])
    ok(mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0001"]}), legacy=[old("已被 ADR-0101 取代")])
    bad("未标", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0001"]}), legacy=[old("已采纳")])
    # 误报与漏报回归
    for fine in ("决定 L2 缓存", "影响面：3 条", "决定 1:1 映射"):
        ok(mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 " + fine]}), mk(no="0100", status="已采纳", extra={**quote, "被部分取代": ["ADR-0101 " + fine]}))
    bad("不得写行号", mk(no="0101", status="已采纳", extra={**quote, "取代": ["ADR-0100 :138"]}), mk(no="0100", status="已采纳", extra=quote))
    bad("须存在、已采纳", mk(no="0100", status="已采纳", extra=quote), legacy=[old("已采纳（范围已被 ADR-0100 取代）")])
    bad("须存在、已采纳", mk(no="0100", status="已采纳", extra=quote), legacy=[old("已被 ADR-0999 取代")])
    bad("重启条件", mk(status="搁置", extra={**quote_only, "理由": "无重启条件"}))
    bad("属用户指派", mk(status="已撤回", extra={"理由": "x"}, secs={**SECS, "背景": "为2026-10-16-131918-x.md所述事项。"}))
    bad("属用户指派", mk(status="已撤回", extra={"理由": "x"}, secs={**SECS, "背景": "用户要求起草。"}))
    assert "无" in b.status_section([], ctx) and "ADR-0100" in b.status_section([adr(mk(date="2026-10-01"))], ctx)
    # 轻量档不得取代
    light = {k: SECS[k] for k in b.LIGHT_REQUIRED}
    ok(mk(tier="轻量", status="已采纳", extra=quote, secs=light))
    bad("轻量档不得取代", mk(no="0101", tier="轻量", status="已采纳", extra={**quote, "取代": ["ADR-0100"]}, secs=light), mk(no="0100", status="已被 ADR-0101 取代"))
    # R-5 小节
    ok(mk(status="提议", secs={"背景": "x", "决定": "y"}))
    bad("缺少小节「后果」", mk(status="已采纳", extra=quote, secs={k: v for k, v in SECS.items() if k != "后果"}))
    bad("缺少小节「证据」", mk(status="已采纳", tier="轻量", extra=quote, secs={k: v for k, v in light.items() if k != "证据"}))
    ok(mk(status="已采纳", tier="轻量", extra=quote, secs={**light, "背景": "x", "后果": "y"}))
    try:
        b.parse_text("src/ADR-0100.md", mk(status="提议").replace("## 决定", "## 附录")); raise AssertionError("未知小节")
    except ValueError:
        pass
    try:
        b.parse_text("src/ADR-0100.md", mk(status="提议", secs={"决定": "y", "背景": "x"}).replace("## 背景\n\nx\n\n## 决定\n\ny", "## 决定\n\ny\n\n## 背景\n\nx")); raise AssertionError("小节乱序")
    except ValueError:
        pass
    # R-6 元数据取值
    bad("类别须取自", mk(category="随便"))
    bad("取值「x.y」不在登记中", mk(bc=("x.y",)))
    bad("取值「TA-9」不在登记中", mk(stage=("TA-9",)))
    ok(mk(bc=("全局",), stage=("全阶段",)))
    bad("须单独成项", mk(bc=("全局", "a.b")))
    ok(mk(bc=("a.b", "c.d")))
    renamed = dict(ctx, bcs={"c.d"})
    bad("取值「a.b」不在登记中", mk(), c=renamed)
    # R-7 大小
    big = {**SECS, "背景": "字" * 7000}
    ok(mk(status="提议", secs=big))
    assert any("超过" in w for w in b.warnings([adr(mk(status="提议", secs=big))], ctx))
    bad("须拆分", mk(status="已采纳", extra=quote, secs=big))
    ok(mk(status="已采纳", extra=quote, secs={**SECS, "背景": "字" * 6000}))
    bad("改用正式档", mk(tier="轻量", status="已采纳", extra=quote, secs={**light, "决定": "字" * 2100}))
    ok(mk(tier="轻量", status="已采纳", extra=quote, secs={**light, "决定": "字" * 1700}))
    # R-9 编号连续：ADR-0029 存在后才失败
    def fake(no):
        return dict(no=no, new=True)
    assert not b.numbering_errors([fake("0001"), fake("0003")])
    assert b.numbering_errors([fake("0001"), fake("0029")])
    assert not b.numbering_errors([fake(f"{n:04d}") for n in range(1, 31)])
    # R-10 超期提议只告警；搁置不计
    assert any("超期提议 ADR-0100" in w for w in b.warnings([adr(mk(date="2026-10-10"))], ctx))
    assert not any("超期" in w for w in b.warnings([adr(mk(date="2026-10-15"))], ctx))
    assert not any("超期" in w for w in b.warnings([adr(mk(date="2026-10-01", status="搁置", extra={**quote_only, "理由": "重启条件：x"}))], ctx))
    # R-14 旁表
    legacy_adrs = [old("已采纳（范围已被 ADR-0101 取代）")]
    entry = dict(id="0001", category="数据", bc=["a.b"], stage=["TA-1"], partially_superseded=["ADR-0101 决定 1"])
    def cat(*entries, adrs=None):
        c = dict(ctx, catalog=list(entries))
        return b.validate_catalog(adrs if adrs is not None else legacy_adrs, c)[0]
    assert not cat(entry)
    assert any("缺少旧稿" in e for e in cat())
    assert any("类别须取自" in e for e in cat(dict(entry, category="乱")))
    assert any("不在登记中" in e for e in cat(dict(entry, bc=["x.y"])))
    assert any("不是旧稿" in e for e in cat(entry, dict(entry, id="0100")))
    assert any("不一致" in e for e in cat(dict(entry, partially_superseded=[])))
    assert any("不一致" in e for e in cat(dict(entry, partially_superseded=["ADR-0102 决定 1"])))
    assert any("重复" in e for e in cat(entry, entry))
    new_in = [adr(mk(no="0100"))]
    assert any("不是旧稿" in e for e in b.validate_catalog(new_in, dict(ctx, catalog=[dict(entry, id="0100")]))[0])
    # R-8 视图可重现，现行有效剔除非采纳，ADR-0020 标注
    a1 = adr(mk(no="0100", status="已采纳", extra=quote)); a2 = adr(mk(no="0101", status="已拒绝", extra={**quote_only, "理由": "x"}))
    idx = b.index_md([a1, a2], {})
    assert idx == b.index_md([a1, a2], {}) and "ADR-0100" in idx.split("## 按类别")[0] and "ADR-0101" not in idx.split("## 按类别")[0]
    assert "### 数据" in idx and "### a.b" in idx
    # 真实文件：旧稿产物逐字节不变，其余新规则对现有 src 全过
    adrs = b.load_adrs(os.path.join(b.BASE, "src"))
    for a in adrs:
        generated = os.path.join(b.BASE, f"ADR-{a['no']}.md")
        if os.path.exists(generated):
            assert b.render(a) == open(generated, encoding="utf-8").read(), f"ADR-{a['no']} 产物与重新渲染不一致"
        assert b.render(a) == open(os.path.join(b.BASE, "src", f"ADR-{a['no']}.md"), encoding="utf-8").read(), f"ADR-{a['no']} 源文件与渲染不一致"
    print(f"build_adrs self-test: ok（{len(adrs)} 份真实 ADR 渲染与源文件逐字节一致）")
