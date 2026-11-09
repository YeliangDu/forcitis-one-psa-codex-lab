"""Git credential protocol guard: fixed Lab HTTPS GET only; never logs credentials."""
import os
import sys
from pathlib import Path

GH='/opt/homebrew/bin/gh'
REPO_PATH=b'YeliangDu/forcitis-one-psa-codex-lab.git'
def main():
    # Git may send store/erase after an attempt. Never delegate those.
    if sys.argv[1:]!=['get']:
        return 0
    raw=sys.stdin.buffer.read(4097)
    if len(raw)>4096 or not raw.endswith(b'\n\n') or b'\x00' in raw or b'\r' in raw:
        return 1
    fields={}
    for row in raw[:-2].split(b'\n'):
        key,sep,value=row.partition(b'=')
        if not sep or key in fields or key not in (b'protocol',b'host',b'path',b'username'):
            return 1
        fields[key]=value
    if (fields.get(b'protocol')!=b'https' or fields.get(b'host')!=b'github.com'
            or fields.get(b'path')!=REPO_PATH
            or fields.get(b'username',b'YeliangDu')!=b'YeliangDu'):
        return 1
    config=Path(os.environ['GH_CONFIG_DIR'])
    if not config.is_absolute() or config.resolve(strict=True)!=config or not config.is_dir():
        return 1
    # Do not consume or capture the helper's stdout. The pipe belongs to Git.
    # Git sends the credential context again through a small anonymous pipe.
    fd,wr=os.pipe()
    try:
        view=memoryview(raw)
        while view:
            sent=os.write(wr,view)
            view=view[sent:]
    finally:
        os.close(wr)
    os.dup2(fd,0);os.close(fd)
    os.execve(GH,[GH,'auth','git-credential','get'],
              {'PATH':'/usr/bin:/bin','HOME':os.environ['HOME'],'GH_CONFIG_DIR':str(config),
               'GH_HOST':'github.com','GH_PROMPT_DISABLED':'1','GH_NO_UPDATE_NOTIFIER':'1'})
    return 1
if __name__=='__main__':
    raise SystemExit(main())
