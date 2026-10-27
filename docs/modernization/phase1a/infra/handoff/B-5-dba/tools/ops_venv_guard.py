"""取证入口的第一道关：入口用 ops-venv 的 python -I -S 启动（-S 不跑 site，不处理 site-packages 里的 .pth）；本模块被导入时，
先由系统解释器另起进程校验 ops-venv 依赖哈希锁，通过后才用 site.addsitedir 把 ops-venv 的 site-packages 加进 sys.path。
不过就退出 3，后面的 SDK 一个都不导入。

校验脚本与锁文件在本机工具库（~/.modernize/psa/tools/one-session/ops_venv_lock/），用 /usr/bin/python3 -I -S 运行，只用标准库，不读 ops-venv。
找不到、起不来、不过都算失败（退出 3）：fail closed。入口须在导入任何第三方包或同目录的 c14_run 等模块之前先 import 本模块。
家目录取自账号库（pwd），不信 HOME 环境变量；子进程只给精简环境变量。同时关掉本进程写 .pyc，避免运行本身改动 ops-venv 里的 __pycache__ 使锁失效。
残余缺口：pyvenv.cfg 的 home 与 bin 链接被改时，入口进程启动即运行在被换的标准库上，进程内守卫无法自证，只有进程外校验能覆盖
（见工具库 ops_venv_lock/SOURCE.md）。
"""
import os
import pwd
import site
import subprocess
import sys

sys.dont_write_bytecode = True
_ROOT = os.path.join(pwd.getpwuid(os.getuid()).pw_dir, '.modernize/psa')
_VERIFY = os.path.join(_ROOT, 'tools/one-session/ops_venv_lock/ops_venv_lock.py')
try:
    _OK = subprocess.run(['/usr/bin/python3', '-I', '-S', _VERIFY, '--verify'], stdin=subprocess.DEVNULL, stdout=sys.stderr,
                         env={'PATH': '/usr/bin:/bin'}).returncode == 0
except (OSError, subprocess.SubprocessError):
    _OK = False
if not _OK:
    sys.stderr.write('ops-venv 依赖校验未过（或校验脚本不可用），取证入口不运行；由 one-ops 查明后重新 --lock\n')
    sys.exit(3)
site.addsitedir(os.path.join(_ROOT, 'ops-venv/lib/python%d.%d/site-packages' % sys.version_info[:2]))
