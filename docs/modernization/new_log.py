#!/usr/bin/env python3
"""新增一条会话日志片段 session-log/<日期>-<HHMMSS>-<会话名>.md（按日志模板预填；session-log 已停，见 SESSION_PLAYBOOK TEAM-15），然后重新生成 SESSION_LOG.md。

用法：python3 docs/modernization/new_log.py <会话名> "<工作单元>" [--date=YYYY-MM-DD]
生成后编辑该片段填写各项，再运行 build_all.py。
"""
import datetime
from new_intent import create, parse_args

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
    opts, (session, unit) = parse_args(__doc__, 2)
    date = opts.get("date", datetime.date.today().isoformat())
    create("session-log", date, session, "", TEMPLATE.format(date=date, session=session, unit=unit))
