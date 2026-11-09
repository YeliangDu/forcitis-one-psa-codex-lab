#!/usr/bin/env python3
"""Test-owned pre-exec barrier; does not interpret a broker request."""
import os,sys
go,ready=map(int,sys.argv[1:3])
argv=sys.argv[4:]
if sys.argv[3]!="--" or not argv or not os.path.isabs(argv[0]):
    raise SystemExit(86)
os.write(ready,b"R");os.close(ready)
if os.read(go,1)!=b"G":
    raise SystemExit(86)
os.close(go)
os.execve(argv[0],argv,dict(os.environ))
