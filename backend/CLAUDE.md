# backend

- `docs/rules/coding-core.md` loads automatically here; it is the must-read. Look up details by id:
- Code: `docs/rules/coding-standard.md` (Java/Spring §2, tests §5, pre-PR self-check §7)
- Naming and packages: `docs/rules/naming-standard.md` §2 and §3; HTTP conventions §2.6
- DDL or migrations (`src/main/resources/db/migration/`): `docs/rules/naming-standard.md` §2.4 and §2.4a, `docs/rules/coding-standard.md` §3, data design per `docs/rules/data-design-checklist.md` §14.1 (migration-in §14.2, outside integration §14.3); a table change needs design's sign-off first (`docs/rules/roles/design.md`)
- New words: look up `docs/glossary/terms.yaml`; if missing, register a proposed term (`docs/glossary/CLAUDE.md`)
- Test containers only from images registered in `backend/architecture/test-images.txt`
