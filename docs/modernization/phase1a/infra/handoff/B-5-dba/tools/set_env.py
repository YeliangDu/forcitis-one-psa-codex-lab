"""set_env.py <file> KEY [KEY2=literal ...]: KEY's value is read from stdin (never printed); KEY=literal sets a non-secret value.
Replaces existing 'KEY=' lines (or appends), keeps mode/owner, writes atomically, prints only key names and value state."""
import os, re, sys, tempfile

path, args = sys.argv[1], sys.argv[2:]
updates = {}
for a in args:
    if "=" in a:
        k, v = a.split("=", 1)
        updates[k] = v
    else:
        updates[a] = sys.stdin.read().strip()
for k, v in updates.items():
    if not re.fullmatch(r"[A-Z0-9_]+", k) or "'" in v or "\n" in v or not v:
        sys.exit(f"refuse {k}: bad key or value")
def render(k, v):
    return f"{k}={v}" if re.fullmatch(r"[A-Za-z0-9_.,:/@+-]+", v) else f"{k}='{v}'"


st = os.stat(path)
lines = open(path, encoding="utf-8").read().split("\n")
seen = set()
for i, line in enumerate(lines):
    m = re.match(r"([A-Z0-9_]+)=", line)
    if m and m.group(1) in updates:
        lines[i] = render(m.group(1), updates[m.group(1)])
        seen.add(m.group(1))
for k in updates:
    if k not in seen:
        lines.insert(len(lines) - 1 if lines and lines[-1] == "" else len(lines), render(k, updates[k]))
fd, tmp = tempfile.mkstemp(dir=os.path.dirname(path), prefix=".one.env.")
with os.fdopen(fd, "w", encoding="utf-8") as f:
    f.write("\n".join(lines))
os.chown(tmp, st.st_uid, st.st_gid)
os.chmod(tmp, st.st_mode & 0o7777)
os.replace(tmp, path)
for k in updates:
    print("set", k, "(replaced)" if k in seen else "(appended)")
