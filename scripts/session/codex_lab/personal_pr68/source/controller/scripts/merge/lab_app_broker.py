"""One-repository installation-token broker. Never emits auth material to logs."""
import argparse,base64,ctypes,json,os,re,signal,socket,stat,struct,subprocess,sys,time
from datetime import datetime,timezone
from pathlib import Path
import urllib.request

OWNER="YeliangDu"
REPO=OWNER+"/forcitis-one-psa-codex-lab"
RID=1449415666
API="https://api.github.com"

def require(ok,reason):
    if not ok:raise ValueError(reason)
def enc(x):return base64.urlsafe_b64encode(x).rstrip(b"=")

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):return None

def request(method,path,auth,data=None):
    require(path.startswith("/") and not path.startswith("//"),"APP_API_PATH")
    headers={"Accept":"application/vnd.github+json","Authorization":"Bearer "+auth,
             "X-GitHub-Api-Version":"2026-03-10","User-Agent":"lab-cprime-credential-broker",
             "Content-Type":"application/json"}
    r=urllib.request.Request(API+path,data=None if data is None else json.dumps(data).encode(),method=method,headers=headers)
    client=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    with client.open(r,timeout=15) as f:
        body=f.read(1024*1024+1);link=f.headers.get("Link","")
        require(len(body)<=1024*1024 and 'rel="next"' not in link,"APP_API_INCOMPLETE")
    return json.loads(body) if body else None

def jwt(app,key):
    info=key.lstat()
    require(key.resolve(strict=True)==key and stat.S_ISREG(info.st_mode) and info.st_nlink==1
            and info.st_uid==os.geteuid() and not info.st_mode & 0o077,"APP_PRIVATE_KEY_STORAGE")
    fd=os.open(key,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    try:
        actual=os.fstat(fd)
        require((actual.st_dev,actual.st_ino)==(info.st_dev,info.st_ino),"APP_KEY_FILE_MOVED")
        now=int(time.time())
        body=enc(b'{"alg":"RS256","typ":"JWT"}')+b"."+enc(json.dumps(dict(iat=now-60,exp=now+480,iss=str(app))).encode())
        signed=subprocess.run(["/usr/bin/openssl","dgst","-sha256","-sign","/dev/fd/"+str(fd)],
                              input=body,capture_output=True,pass_fds=(fd,),timeout=10)
        require(signed.returncode==0 and 128<=len(signed.stdout)<=1024,"APP_JWT_SIGN_FAILED")
        return (body+b"."+enc(signed.stdout)).decode()
    finally:os.close(fd)

def validate_permissions(permissions, write):
    allowed={"metadata":"read","contents":"write" if write else "read"}
    require(type(permissions) is dict and permissions.get("metadata","read")=="read"
            and all((k in allowed and v==allowed[k]) or (k in ("pull_requests","actions","checks") and v=="read")
                    for k,v in permissions.items()) and (not write or permissions.get("contents")=="write"),
            "APP_PERMISSIONS_TOO_BROAD_OR_MISSING_WRITE")

def identity_and_token(app,key):
    j=jwt(app,key)
    meta=request("GET","/app",j);require(meta["id"]==app and meta["owner"]["login"]==OWNER,"APP_OWNER_MISMATCH")
    validate_permissions(meta["permissions"],True)
    install=request("GET","/repos/"+REPO+"/installation",j)
    require(install["app_id"]==app and install["account"]["login"]==OWNER and
            install["repository_selection"]=="selected" and install.get("suspended_at") is None,"APP_INSTALLATION_SCOPE")
    rows=request("GET","/app/installations?per_page=100",j)
    require(type(rows) is list and len(rows)==1 and rows[0]["id"]==install["id"],"APP_MORE_THAN_ONE_INSTALLATION")
    path="/app/installations/"+str(install["id"])+"/access_tokens"
    # Enumerate the actual selected repos with a metadata-only token, not a
    # narrowed write token that could hide a broader installation.
    inventory=request("POST",path,j,{"permissions":{"metadata":"read"}})
    try:
        require(inventory["permissions"].get("metadata")=="read" and set(inventory["permissions"])=={"metadata"},
                "APP_INVENTORY_TOKEN_NOT_READONLY")
        listing=request("GET","/installation/repositories?per_page=100",inventory["token"])
        require(listing["total_count"]==len(listing["repositories"])==1 and
                (listing["repositories"][0]["id"],listing["repositories"][0]["full_name"])==(RID,REPO),
                "APP_INSTALLATION_NOT_ONLY_LAB")
    finally:request("DELETE","/installation/token",inventory["token"])
    scoped=request("POST",path,j,{"repository_ids":[RID],"permissions":{"contents":"write"}})
    try:
        require(scoped["permissions"].get("contents")=="write" and set(scoped["permissions"])<={"contents","metadata"},
                "APP_WRITE_TOKEN_SCOPE")
        listing=request("GET","/installation/repositories?per_page=100",scoped["token"])
        require(listing["total_count"]==len(listing["repositories"])==1 and
                (listing["repositories"][0]["id"],listing["repositories"][0]["full_name"])==(RID,REPO),
                "APP_WRITE_TOKEN_NOT_ONLY_LAB")
        return dict(app_id=app,app_slug=meta["slug"],installation_id=install["id"],repo_id=RID,
                    selection="selected",installed_repositories=1,permissions=scoped["permissions"],
                    token_expires_at=scoped["expires_at"]),scoped["token"]
    except BaseException:
        request("DELETE","/installation/token",scoped["token"])
        raise

def peer_uid(conn):
    if hasattr(socket,"SO_PEERCRED"):
        return struct.unpack("3i",conn.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))[1]
    uid,gid=ctypes.c_uint(),ctypes.c_uint()
    r=ctypes.CDLL(None).getpeereid(conn.fileno(),ctypes.byref(uid),ctypes.byref(gid))
    require(r==0,"APP_PEER_IDENTITY_UNAVAILABLE");return uid.value

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--app-id",required=True,type=int);p.add_argument("--key",required=True,type=Path)
    p.add_argument("--socket",required=True,type=Path);a=p.parse_args()
    token=None;owned=False;identity=None;exit_code=1
    try:
        os.umask(0o077)
        parent=a.socket.parent;st=parent.lstat()
        require(parent.is_absolute() and parent.resolve(strict=True)==parent and stat.S_ISDIR(st.st_mode)
                and st.st_uid==os.geteuid() and not st.st_mode&0o077 and not a.socket.exists() and not a.socket.is_symlink(),
                "APP_PRIVATE_SOCKET_DIRECTORY_REQUIRED")
        require(a.key.is_absolute(),"APP_PRIVATE_KEY_ABSOLUTE")
        identity,token=identity_and_token(a.app_id,a.key)
        expires=datetime.fromisoformat(identity["token_expires_at"]).timestamp()
        with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as server:
            server.bind(str(a.socket));owned=True;a.socket.chmod(0o600);server.listen(4);server.settimeout(.5)
            print(json.dumps(dict(identity,stage="ready"),sort_keys=True),flush=True)
            running=True
            def stop(*_):
                nonlocal running
                running=False
            signal.signal(signal.SIGINT,stop);signal.signal(signal.SIGTERM,stop)
            while running and time.time()<expires-10:
                try:conn,_=server.accept()
                except TimeoutError:continue
                with conn:
                    conn.settimeout(2)
                    try:
                        if peer_uid(conn)!=os.geteuid():continue
                        with conn.makefile("rb") as f: msg=f.readline(80)
                        if msg==b"lab-https-installation-password\n" and time.time()<expires-10:
                            conn.sendall(token.encode()+b"\n")
                    except (OSError,ValueError):continue
        exit_code=0
    except Exception as e:
        print(json.dumps(dict(stage="hold",error_type=type(e).__name__,reason=str(e) if type(e) is ValueError else "credential-initialization-or-network-failed")),flush=True)
    finally:
        if owned:a.socket.unlink(missing_ok=True)
        if token:
            try:
                request("DELETE","/installation/token",token)
                print('{"stage":"token-revoked"}',flush=True)
            except Exception:
                print('{"stage":"revocation-unverified"}',flush=True);exit_code=1
    return exit_code

if __name__=="__main__":raise SystemExit(main())
