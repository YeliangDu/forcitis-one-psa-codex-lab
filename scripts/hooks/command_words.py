#!/usr/bin/env python3
"""兼容层（GOV-1023-FV2 O2）：命令解析已并入 scripts/gates/prod_write_gate.py，这里只转出旧名字给还没迁走的 codex_stdin_gate（第二批乙随它删除；pre_push_gate、precheck_before_pr 已在 O6 删除，main_checkout_gate 已在 O4 搬到 scripts/gates 且不再依赖本层）。
这些钩子在 O4、O6、O7 与第二批乙重写或删除后，本文件随之删除。自测在 scripts/gates/prod_write_gate_selftest.py。"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "scripts", "gates"))
from prod_write_gate import (ENV, HEREDOC, KEYWORDS, SEPARATORS, SHELLS, WRAPPERS, Command, decode_ansi_c, expand, heredoc_bodies, name_of, neutralize, plain,  # noqa: E402,F401
                             restore, simple_commands, split_heredocs, tokens_of, unquote_dollar, without_heredoc_bodies)

if __name__ == "__main__":
    sys.exit(0)
