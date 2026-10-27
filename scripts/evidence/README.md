# 取证单入口

本目录放取证单入口 `evidence.py` 与取证单 `sheet_<名>.py`（二乙 #6 O22a；定稿 2.6、1.12）。

## 规则（调度 10-27 定）

1. 新增或改动取证单一律走首轮复审加 opus 终审；PR 里写明这张取证单「整支只读」的依据：连什么、用哪个只读账号、只读不写的理由，以及它导入的仓库内模块。
2. 登记行由作者运行 `evidence.py --entry` 生成，再手贴进 `scripts/gates/prod_targets.json` 的 `trusted_entries.entries`。`evidence_selftest.py` 比对两者，防止漂移。不让脚本改门的表。

## 入口用法

- 运行方式固定为 `/usr/bin/python3 -I -S`，参数是 `--session=<会话>` 和取证单名。登记写法见 `prod_targets.json` 的 evidence 一条，其他写法门按不可信处理。
- 入口运行本目录的 `sheet_<名>.py` 的 `collect(session)`，每行一条文本。输出写到 `~/.modernize/psa/ops/evidence/`，目录 0700、文件 0600，文件名带 `expires-YYYYMMDD`，文件头第一行写「过期：」。到期由 janitor 处理。过期天数取 `policy.json` 的 `retention_days.evidence`。
- 标准输出只打印输出文件路径。取证单出错时只报异常类型。
- 无口令：取证单运行时，标准输入接到 /dev/null，getpass 直接失败。凭据只来自专用只读账号的本机配置。取证单自己打开 /dev/tty 要口令的情况入口拦不住，由复审把关。
- 门信任这个入口有两个条件：一是 origin/main 上登记了；二是入口、本目录全部 sheet_*.py 以及它们传递导入的仓库内文件，都与 origin/main 逐字节一致。
- 取证单在 `-I -S` 下运行，只能用标准库与仓库内模块。需要阿里云 SDK 的取证单本入口跑不了，随 #7 再定。
