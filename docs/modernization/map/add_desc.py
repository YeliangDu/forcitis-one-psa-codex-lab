import json, sys, os
p = "descriptions.json"
d = json.load(open(p)) if os.path.exists(p) else {}
d[sys.argv[1]] = sys.stdin.read().strip()
json.dump(d, open(p, "w"), ensure_ascii=False, indent=1); print(len(d))
