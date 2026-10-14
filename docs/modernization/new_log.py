#!/usr/bin/env python3
"""新增一条会话日志片段 session-log/<日期>-<序号>-<会话名>.md（按 SESSION_PLAYBOOK §8 模板预填），然后重新生成 SESSION_LOG.md。

用法：python3 docs/modernization/new_log.py <会话名> "<工作单元>" [--date=YYYY-MM-DD]
生成后编辑该片段填写各项，再运行 build_all.py。
"""
import datetime, os, sys
from new_intent import BASE, next_seq, short_name
import subprocess

TEMPLATE = """## {date} · {session} · {unit}
- 目标：
- 输入（读过的状态文件、上一条日志）：
- 用户决定（原话）：
- 产出（文件 / 提交 / 发布的页面）：
- 验证（跑了什么、结果）：
- 未完成与阻塞：
- 下一步命令：
- 会话 ID（用于 claude --resume）：
"""

if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--date")]
    date = next((a.split("=", 1)[1] for a in sys.argv[1:] if a.startswith("--date=")), datetime.date.today().isoformat())
    if len(args) != 2:
        sys.exit(__doc__)
    session, unit = args
    folder = os.path.join(BASE, "session-log")
    path = os.path.join(folder, f"{date}-{next_seq(folder):03d}-{short_name(session, 40)}.md")
    open(path, "x", encoding="utf-8").write(TEMPLATE.format(date=date, session=session, unit=unit))
    print(os.path.relpath(path, BASE))
    subprocess.run([sys.executable, os.path.join(BASE, "build_records.py")], check=True)
