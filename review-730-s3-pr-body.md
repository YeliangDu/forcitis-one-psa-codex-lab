Refs #730

#730 S3: HTTP API for permission-adjustment review items under `/api/v1/platform/permission-adjustments` (list, get by id, `:uphold`, `:mark-adjusted`), relation scope fail-closed (out of scope = denied-access audit + 404), about-self commands 403, If-Match versioning, uphold of a sensitive-role review needs an employed business owner (not the subject), expiry = next natural quarter start 00:00 Asia/Shanghai, mark-adjusted takes exactly one existing reference. Rulings: #730 comments 6602368110, 6602101721, 6604945364, 6605327331; ADR-0032/0044/0081/0082. Acceptance: #1119 (final head 6b861819b), merged unchanged; the one acceptance-directory edit is the two role pins in `SystemRoleDeclarationIT` under `Acceptance-Ruling: RULING-LEAD-20261103-16` (commit 5c8d134bf, sonnet review 通过).

## Choices to check
- `platform.web.ProblemDetails`: every NOT_FOUND now carries a fixed `instance` urn (`urn:forcitis:error:not-found`) instead of the request path, so an out-of-scope id and a missing id give byte-identical bodies and the id is not echoed. Shared by all modules; the opus reviewer grepped backend tests, frontend and docs and found no reader of `instance`.
- Reader rule exists in Java (`PermissionAdjustmentReader`) and SQL (`findFor`); both covered by `PermissionAdjustmentReadApiIT`.
- mark-adjusted on a recipient-addressed scope review uses the same handler rule as uphold (acceptance does not pin it).
- `GlossaryTest`/`term_rules.py`: "upheld" added to the irregular past-tense words; new proposed terms in `terms.yaml` and `platform-identity.yaml`.
- Two GET paths registered SCOPED in `data-scope-read-paths.txt` (guard added on main after this head).

## Defence | how broken | red
| Defence | How broken | Red |
|---|---|---|
| about-self 403 before scope | `isSubject` check `if (false)` | red (2 cases) |
| out-of-scope = 404 + denied audit | `canRead` check `if (false)` | red (5 + 5) |
| ExpectedVersion check | `expected.check` removed | red (1) |
| handler rule (recipient / update) | `canHandle` check `if (false)` | red (4) |
| owner must be employed | employment check `false` | red (1) |
| no id echo in 404 | NOT_FOUND `instance` branch off | red |
| quarter expiry | `+4` -> `+3` | red |
| exactly one reference | only "neither" refused | red |

(Full opus tables: review-730-s3-opus.md.) 

🤖 Generated with [Claude Code](https://claude.com/claude-code)
