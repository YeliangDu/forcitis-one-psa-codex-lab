set -euo pipefail
w="$1"; uid="$2"; gid="$3"; user="$4"; hostnet="$5"; source_root="$6"; acceptance="$7"
trap 'if test -n "${sshd_pid:-}"; then kill "$sshd_pid" 2>/dev/null || true; wait "$sshd_pid" || true; fi' EXIT
test "$(readlink /proc/self/ns/net)" != "$hostnet"
ip link set lo up
test "$(ip -o link show | wc -l)" -eq 1
mount -t tmpfs -o mode=0755,nosuid,nodev tmpfs /run
mkdir -m 0755 /run/sshd
# Set a disposable home and an unusable password in mount-private files.
awk -F: -v OFS=: -v u="$user" -v h="$w/home" '$1==u {$6=h; $7="/bin/bash"; n++} {print} END {if(n!=1) exit 1}' /etc/passwd > /run/fixture.passwd
awk -F: -v OFS=: -v u="$user" '{$2="*"} $1==u {$3=20000; $7=""; $8=""; n++} {print} END {if(n!=1) exit 1}' /etc/shadow > /run/fixture.shadow
chmod 0644 /run/fixture.passwd
chmod 0600 /run/fixture.shadow
mount --bind /run/fixture.passwd /etc/passwd
mount --bind /run/fixture.shadow /etc/shadow
install -d -m 700 -o "$uid" -g "$gid" "$w/home"
ssh-keygen -q -t ed25519 -N '' -f /run/lab-host
as_user() { setpriv --reuid "$uid" --regid "$gid" --clear-groups --no-new-privs -- env -i PATH=/usr/bin:/bin HOME="$w/home" LAB_USER="$user" "$@"; }
as_user ssh-keygen -q -t ed25519 -N '' -f "$w/client"
as_user ssh-keygen -q -t ed25519 -N '' -f "$w/wrong-client"
cp "$w/client.pub" "$w/home/authorized_keys"
{ printf '[127.0.0.1]:40363 '; cut -d' ' -f1,2 /run/lab-host.pub; } > "$w/known_hosts"
chown "$uid:$gid" "$w/home/authorized_keys" "$w/known_hosts"
chmod 600 "$w/home/authorized_keys"
cat > "$w/force.sh" <<FORCE
#!/bin/bash
set -eu
case "\$SSH_ORIGINAL_COMMAND" in
  "git-receive-pack '/receiver'") exec /usr/bin/git-receive-pack "$w/receiver.git" ;;
  "git-upload-pack '/receiver'") exec /usr/bin/git-upload-pack "$w/receiver.git" ;;
  *) exit 77 ;;
esac
FORCE
chmod 755 "$w/force.sh"
cat > "$w/client.conf" <<CFG
Host *
  User $user
  IdentityFile $w/client
  IdentityAgent none
  IdentitiesOnly yes
  StrictHostKeyChecking yes
  UserKnownHostsFile $w/known_hosts
  GlobalKnownHostsFile /dev/null
  ProxyCommand none
  ProxyJump none
  ControlMaster no
  BatchMode yes
  ConnectTimeout 3
CFG
cat > /run/lab-sshd.conf <<CFG
Port 40363
AddressFamily inet
ListenAddress 127.0.0.1
HostKey /run/lab-host
PidFile /run/lab-sshd.pid
UsePAM no
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
AuthenticationMethods publickey
PermitRootLogin no
AllowUsers $user
AuthorizedKeysFile $w/home/authorized_keys
DisableForwarding yes
PermitTTY no
PermitUserRC no
PermitUserEnvironment no
PrintMotd no
UseDNS no
ForceCommand $w/force.sh
CFG
/usr/sbin/sshd -t -f /run/lab-sshd.conf
/usr/sbin/sshd -T -f /run/lab-sshd.conf | grep -E '^(listenaddress|usepam|passwordauthentication|kbdinteractiveauthentication|authenticationmethods|disableforwarding|permittty) '
/usr/sbin/sshd -D -e -f /run/lab-sshd.conf > "$w/server.log" 2>&1 &
sshd_pid=$!
/usr/bin/python3 -I -B - "$sshd_pid" "$w/listener.json" <<'IDENTITY'
import json, pathlib, sys
pid=int(sys.argv[1]); proc=pathlib.Path('/proc')/str(pid)
stat=proc.joinpath('stat').read_text().rsplit(')',1)[1].split()
assert proc.joinpath('exe').resolve(strict=True)==pathlib.Path('/usr/sbin/sshd').resolve(strict=True)
pathlib.Path(sys.argv[2]).write_text(json.dumps(dict(pid=pid,start=stat[19],exe='/usr/sbin/sshd')))
IDENTITY
chmod 644 "$w/listener.json"
for _ in $(seq 1 80); do ss -H -ltn | grep -q '127.0.0.1:40363 ' && break; kill -0 "$sshd_pid"; sleep .05; done
test "$(ss -H -ltn | awk '{print $4}')" = '127.0.0.1:40363'
if as_user env EXACT_B_SOURCE_ROOT="$source_root" WIRE_WORK="$w" /usr/bin/python3 -I -B "$acceptance" -v; then
  :
else
  test_rc=$?
  tail -n 20 "$w/server.log"
  if test -f "$w/git.stderr"; then tail -n 20 "$w/git.stderr"; fi
  exit "$test_rc"
fi
