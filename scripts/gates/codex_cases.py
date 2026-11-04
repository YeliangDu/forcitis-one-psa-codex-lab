"""Commands the codex gate must judge one way (#1206): the word codex in text is no use of it, running it is. Shared by the gate's self-test and the pre-filter parity test (gates_selftest.py, codex_gate section).
ALLOWED: the main gate lets these through (cwd = a session worktree). REFUSED: it must refuse these. PREFILTER_ALLOWS: the part of ALLOWED the mounted pre-filter (settings.json) also lets through when the gate itself has crashed.
The pre-filter may refuse more than the gate, never less: over ALLOWED + REFUSED its allowed set must be a subset of the gate's allowed set."""

ALLOWED = [
    'git commit -m "fix: the codex gate"',
    "git commit -m 'codex pilot'",
    'gh pr create --title "x" --body "uses codex for drafting"',
    "grep -n codex file",
    "grep -rn 'codex' docs scripts",
    "rm scratch/codex-prompt.md",
    "rm -f codex-out.txt codex-prompt.md",
    "cat codex-out.txt",
    "cat <<'EOF'\nuse codex here\nEOF",
    "cat > codex-prompt.md <<'EOF'\nasking codex to review\nEOF",
    "git commit -m \"$(cat <<'EOF'\nfix: codex gate\nEOF\n)\"",
    "gh pr create --body-file - <<'EOF'\nabout codex\nEOF",
    "echo codex | tee codex-note.txt",
    "sed -i '' 's/codex/x/' notes.md",
    "ls docs/codex && cat docs/codex/a.md",
    "mv codex-a.md codex-b.md",
    "wc -l codex-out.txt",
    "head -50 codex-out.txt | grep -n codex",
    'gh issue comment 1 --body "ran codex exec, see codex-out.txt"',
    'gh issue comment 1 --body "$(cat codex-out.txt)"',
    "python3 scripts/merge/post_review.py --pr 1 --body-file codex-review.md",
    "echo 'codex exec x' > note.txt",
    "diff codex-a.txt codex-b.txt",
    "cp /tmp/x codex-copy.txt",
    "touch codex-lock",
    "tail -f codex-out.txt",
    "bash -c 'echo codex'",
    "sh -c \"grep -n codex f\"",
    "zsh -c 'git commit -m \"codex\"'",
    "bash -lc 'ls docs/codex'",
    "eval 'echo codex'",
    "bash <<'EOF'\necho codex\nEOF",
]

REFUSED = [
    "codex", "codex exec x", "/usr/local/bin/codex exec x", "./codex exec x", "~/bin/codex exec x",
    "env codex exec x", "xargs codex", "echo x | xargs codex exec", "sh -c 'codex exec x'", "bash -c 'codex exec x'", "zsh -c 'codex exec x'", "eval 'codex exec x'", 'eval "codex exec x"',
    "npx codex exec x", "nohup codex exec x", "timeout 5 codex exec x", "exec codex exec x", "command codex exec x", "time codex exec x", "nice codex exec x", "sudo codex exec x",
    "time -p codex exec x", "builtin command codex exec x", "stdbuf -o0 codex exec x",
    "$(codex exec x)", "echo $(codex exec x)", "`codex exec x`", "echo `codex exec x`",
    "c[o]dex exec x", "co{dex,} exec x", "cod?x exec x", "/opt/bin/c[o]dex exec x", "bash -c 'c[o]dex exec x'", "eval 'cod?x exec x'", 'eval "co{dex,} exec x"',
    "ls; codex exec x", "ls && codex exec x", "ls | codex exec x", "X=1 codex exec x", "X=codex; $X exec x",
    "codex exec --full-auto 'a b'", "codex exec 'a b'", "codex --sandbox read-only 'a b'",
    "echo codex | bash", "echo 'codex exec x' | sh", 'bash -c "$(echo codex) exec x"', "bash <<'EOF'\ncodex exec x\nEOF",
    # inline code of an interpreter that names the tool stays refused: whether it runs it cannot be read (#1206 ruling 1)
    "python3 - <<'EOF'\nprint('codex')\nEOF", "python3 -c \"print('codex')\"", "node -e \"console.log('codex')\"", "perl -e 'print qq(codex)'", "awk '/codex/ {print}' f",
    "python3 - <<'EOF'\nimport subprocess\nsubprocess.run(['codex', 'exec', 'x'])\nEOF",
    # a harmless program told to run it
    "git -c alias.x='!codex exec y' x", "git -c core.pager=codex log", "git config alias.x '!codex exec y'", "GIT_PAGER=codex git log", "rg --pre codex x", "sort --compress-program=codex x",
    "git ls-remote --upload-pack codex origin",
]

PREFILTER_ALLOWS = [
    'git commit -m "fix: the codex gate"', "git commit -m 'codex pilot'", 'gh pr create --title "x" --body "uses codex for drafting"', "grep -n codex file", "grep -rn 'codex' docs scripts",
    "rm scratch/codex-prompt.md", "rm -f codex-out.txt codex-prompt.md", "cat codex-out.txt", "echo codex | tee codex-note.txt", "sed -i '' 's/codex/x/' notes.md", "ls docs/codex && cat docs/codex/a.md",
    "mv codex-a.md codex-b.md", "wc -l codex-out.txt", "head -50 codex-out.txt | grep -n codex", "echo 'codex exec x' > note.txt", "diff codex-a.txt codex-b.txt", "cp /tmp/x codex-copy.txt", "touch codex-lock",
    "tail -f codex-out.txt",
]
