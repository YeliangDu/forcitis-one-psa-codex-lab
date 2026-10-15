# 同名异义样例

同一用语在不同 bc 含义不同时，逐 bc 定叫法与边界（用户 2026-10-15：「同一用语在不同bc含义不同，比如项目。」intent 2026-10-15-171649-one-coordinator）。

| 卡 | 内容 | 状态 |
|---|---|---|
| `C-PROJECT.json` | 「项目」在 13 个 bc 的 22 种含义 | 在线页待用户确认 |

页面：`python3 docs/modernization/glossary/gate/build_gate_page.py C-PROJECT <输出> --by-bc`，发布到固定链接（见 `gate/README.md`）。答复用 `gate/export_decisions.py C-PROJECT <out_dir>` 导出到 `glossary/answers/C-PROJECT.json`，不自动写回：本卡有沿用已有词条的条目，按 B1 的 bc 隔离规则由 one-glossary 写回。
