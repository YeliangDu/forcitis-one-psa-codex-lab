#!/bin/bash
hook_dir=$(CDPATH= cd -- "${BASH_SOURCE[0]%/*}" 2>/dev/null && pwd) || {
  printf '%s\n' '【交接快照】告警：找不到钩子目录；请手动交接。'
  exit 0
}
python3 -I "$hook_dir/handoff_snapshot.py" --start 2>/dev/null || printf '%s\n' '【交接快照】告警：恢复脚本不可用；请手动读取上下文。'
exit 0
