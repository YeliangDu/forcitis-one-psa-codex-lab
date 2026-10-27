#!/usr/bin/env python3
"""兼容层（GOV-1023-FV2 O2）：命令解析已并入 scripts/gates/prod_write_gate.py，这里只转出旧名字。
最后一个导入者 codex_stdin_gate 已在第二批乙 O21b 删除（直接调用 codex、pi 改由 .claude/settings.json 的 permissions.deny 拒），
它专用的 here-document／注释读法随之删去；本文件已无运行时读者，只剩 gates_selftest.py 把它当作 scripts/hooks 下的样本文件，删除另行定。"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "scripts", "gates"))
from prod_write_gate import (ENV, KEYWORDS, SEPARATORS, SHELLS, WRAPPERS, Command, decode_ansi_c, expand, name_of, neutralize, plain,  # noqa: E402,F401
                             restore, simple_commands, unquote_dollar)


if __name__ == "__main__":
    sys.exit(0)
