#!/usr/bin/env python3
"""Exact Lab askpass; stdout is Git's anonymous credential pipe."""
import os,re,socket,sys
from pathlib import Path
import stat

REPO="YeliangDu/forcitis-one-psa-codex-lab.git"

def main():
    if len(sys.argv)!=2:return 1
    p=sys.argv[1]
    if re.fullmatch(r"Username for 'https://github\.com/"+re.escape(REPO)+r"': ?",p):
        os.write(1,b"x-access-token\n");return 0
    if not re.fullmatch(r"Password for 'https://x-access-token@github\.com/"+re.escape(REPO)+r"': ?",p):
        return 1
    try:
        path=Path(os.environ["LAB_CREDENTIAL_SOCKET"])
        st=path.lstat();parent=path.parent.lstat()
        if not(path.is_absolute() and path.resolve(strict=True)==path and stat.S_ISSOCK(st.st_mode)
               and stat.S_ISDIR(parent.st_mode) and st.st_uid==parent.st_uid==os.geteuid()
               and not(st.st_mode|parent.st_mode)&0o077):return 1
        with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as s:
            s.settimeout(3);s.connect(str(path));s.sendall(b"lab-https-installation-password\n")
            with s.makefile("rb") as f:secret=f.readline(16385)
        if not (0<len(secret)<16385 and secret.endswith(b"\n") and re.fullmatch(rb"[!-~]+\n",secret)):return 1
        os.write(1,secret)
        return 0
    except (OSError,KeyError,ValueError):return 1

if __name__=="__main__":raise SystemExit(main())
