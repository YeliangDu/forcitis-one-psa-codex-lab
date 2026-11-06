# Codex first-draft preamble
Your prompt starts with: "Read docs/rules/codex-preamble.md and the card, then write the draft." You run in `codex exec --sandbox workspace-write -c sandbox_workspace_write.network_access=true --add-dir <home>/.m2`: the worktree, `/tmp`, `$TMPDIR` and `~/.m2` are writable and the network is on, so Maven resolves and downloads what it needs. Docker and the git dir are not available. You compile and run the unit tests yourself and hand back only when they are green.

## Test first
For a behaviour change or a fix, write the failing unit test first and run it red, then write the code and run it green. Hand back the red run and the green run of that test.

## Run these and leave them green
- `cd backend && ./mvnw -B -q compile` (if it finds no Java, set JAVA_HOME to the installed JDK for that command and say so)
- `cd backend && ./mvnw -B -q test-compile`
- the unit tests of the classes you changed and of the guards that touch your change: `cd backend && ./mvnw -B -q test-compile surefire:test -Dtest=<ClassA>,<ClassB> -Djacoco.skip=true`. Pick the class a guard message names, or the tests of the package you changed. Mockito loads as a java agent from the pom, so mock-using classes run too.
- the full guard set: `cd backend && ./mvnw -B -q test-compile surefire:test -Dtest=$(python3 ../scripts/ci/light_unit_classes.py) -Djacoco.skip=true`. Run it before hand-back; only the classes listed in `docs/rules/codex-false-red.txt` may be red. Any other red class is your change.
- `bash scripts/ci/light.sh`
- from `frontend/`: `node ./scripts/check-copy-glossary.mjs` and `node --test ./scripts/copy-glossary.test.mjs` (the other tests there read `openapi.json` and fail without it)
- read-only git: `git status`, `git diff`, `git log`

## Stays with the dev session, never run or rely on
- failsafe, `*IT` classes, Testcontainers, `docker`: the session runs the integration tests
- `generate_api.sh`, `typecheck`, `pnpm run`
- `git add`, `git commit`, `git branch`, `git checkout -b`, `git push`, `gh`: the dev session commits and opens the PR
A missing `openapi.json` or a missing generated `schema.ts` in a class listed in `docs/rules/codex-false-red.txt` is the sandbox (it needs Docker), not your change: report it, do not fix it. A Mockito error is not in that list: it is a real problem, report it. Never loosen or delete an assertion to get green.

## Hand back
The exact commands you ran, each with its real pass/fail counts (copy the surefire `Tests run:` line), then the files you changed. A check you could not make green stays listed as red. The dev session puts your session id on the PR as a `Codex: <session-id>` line.
