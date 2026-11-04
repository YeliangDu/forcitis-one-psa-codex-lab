# Codex first-draft preamble
Your prompt starts with: "Read docs/rules/codex-preamble.md and the card, then write the draft." You run in `codex exec --sandbox workspace-write`: the worktree, `/tmp` and `$TMPDIR` are writable; network, `~/.m2` writes, Docker and the git dir are not. Run the fast checks yourself and hand back only when they are green.

## Run these and leave them green
- `cd backend && ./mvnw -B -q -o test-compile` (if it finds no Java, set JAVA_HOME to the installed JDK for that command and say so)
- named guard classes that touch your change: `cd backend && ./mvnw -B -q -o test-compile surefire:test -Dtest=<ClassA>,<ClassB> -Djacoco.skip=true`. Pick the class a guard message names, or the tests of the package you changed; take only classes that use neither Mockito nor sockets (`grep -L -E "Mockito|Socket|WireMock" <file>` lists them).
- `bash scripts/ci/light.sh`
- from `frontend/`: `node scripts/check-copy-glossary.mjs` and `node --test scripts/copy-glossary.test.mjs` (the other tests there read `openapi.json` and fail without it)
- read-only git: `git status`, `git diff`, `git log`

## Never run or rely on
- the full guard set (`light_unit_classes.py` as the `-Dtest` value): Mockito cannot attach in the sandbox, so hundreds of errors are false
- failsafe, `*IT` classes, Testcontainers, `docker`
- `generate_api.sh`, `typecheck`
- `pnpm run`
- anything that needs the network: Maven downloads, `git fetch`, `git push`, `gh`
- `git add`, `git commit`, `git branch`, `git checkout -b`: the dev session commits
A Mockito attach error, a `SocketException: Operation not permitted` or a missing `openapi.json` is the sandbox, not your change: report it, do not fix it. Never loosen or delete an assertion to get green.

## Hand back
The exact commands you ran, each with its real pass/fail counts (copy the surefire `Tests run:` line), then the files you changed. A check you could not make green stays listed as red.
