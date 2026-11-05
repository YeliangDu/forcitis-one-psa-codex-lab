# Codex first-draft preamble
Your prompt starts with: "Read docs/rules/codex-preamble.md and the card, then write the draft." You run in `codex exec --sandbox workspace-write`: the worktree, `/tmp` and `$TMPDIR` are writable; network, `~/.m2` writes, Docker and the git dir are not. Run the fast checks yourself and hand back only when they are green.

## Run these and leave them green
- `cd backend && ./mvnw -B -q -o test-compile` (if it finds no Java, set JAVA_HOME to the installed JDK for that command and say so)
- named guard classes that touch your change: `cd backend && ./mvnw -B -q -o test-compile surefire:test -Dtest=<ClassA>,<ClassB> -Djacoco.skip=true`. Pick the class a guard message names, or the tests of the package you changed. Mockito loads as a java agent from the pom, so mock-using classes run too.
- the full guard set: `cd backend && ./mvnw -B -q -o test-compile surefire:test -Dtest=$(python3 ../scripts/ci/light_unit_classes.py) -Djacoco.skip=true`. Run it before hand-back; only the classes listed in `docs/rules/codex-false-red.txt` may be red (sandbox: no sockets, no generated `schema.ts`). Any other red class is your change.
- `bash scripts/ci/light.sh`
- from `frontend/`: `node ./scripts/check-copy-glossary.mjs` and `node --test ./scripts/copy-glossary.test.mjs` (the other tests there read `openapi.json` and fail without it)
- read-only git: `git status`, `git diff`, `git log`

## Never run or rely on
- failsafe, `*IT` classes, Testcontainers, `docker`
- `generate_api.sh`, `typecheck`
- `pnpm run`
- anything that needs the network: Maven downloads, `git fetch`, `git push`, `gh`
- `git add`, `git commit`, `git branch`, `git checkout -b`: the dev session commits
A `SocketException: Operation not permitted`, a missing `openapi.json` or a missing generated `schema.ts` in a class listed in `docs/rules/codex-false-red.txt` is the sandbox, not your change: report it, do not fix it. A Mockito error is not in the sandbox list: it is a real problem, report it. Never loosen or delete an assertion to get green.

## Hand back
The exact commands you ran, each with its real pass/fail counts (copy the surefire `Tests run:` line), then the files you changed. A check you could not make green stays listed as red.
