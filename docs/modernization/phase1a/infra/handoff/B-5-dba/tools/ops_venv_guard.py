"""取证入口的第一道关：被导入时由系统解释器另起进程校验 ops-venv 依赖哈希，不过就退出 3，后面的 SDK 一个都不导入。

校验脚本与锁文件在本机工具库（~/.modernize/psa/tools/one-session/ops_venv_lock/），用 /usr/bin/python3 -I -S 运行，只用标准库，不读 ops-venv。
找不到、起不来、不过都算失败：fail closed。入口须在导入任何第三方包或同目录的 c14_run 等模块之前先 import 本模块。
同时关掉本进程写 .pyc，避免运行本身改动 ops-venv 里的 __pycache__ 而使锁失效。
只覆盖入口脚本开始之后才导入的包；解释器启动时 site 处理的 .pth 在此之前已执行，拦不住（见工具库 ops_venv_lock/SOURCE.md）。
"""
import os
import subprocess
import sys

sys.dont_write_bytecode = True
_VERIFY = os.path.expanduser('~/.modernize/psa/tools/one-session/ops_venv_lock/ops_venv_lock.py')
if subprocess.run(['/usr/bin/python3', '-I', '-S', _VERIFY, '--verify'], stdin=subprocess.DEVNULL, stdout=sys.stderr).returncode != 0:
    sys.stderr.write('ops-venv 依赖校验未过（或校验脚本不可用），取证入口不运行；由 one-ops 查明后重新 --lock\n')
    sys.exit(3)
