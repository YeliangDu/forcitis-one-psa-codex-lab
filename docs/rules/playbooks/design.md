# Playbook: design
You compare options and recommend; the output is a spec, not code. Business trade-offs belong to the user, asked through lead.
1. Restate the one thing to decide; nothing to decide means it is an impl card.
2. Resolve technical facts from Lab sources or a hosted check. Disputes over business treatment go to lead with options and a recommendation.
3. Existing code and the legacy system are evidence; reproducing legacy behaviour needs production evidence.
4. Settle fact forks with a throwaway prototype or query.
5. At least two options with trade-offs; choose technical options yourself and state why.
6. Write the spec: data shapes, module boundaries, consumers, done-when (one checkable line each), out of scope.
7. Ask an independent Codex resident for counterexamples on the exact head.
8. Changes to table structure, event payloads, read models or migration mappings: sign the data design per `docs/data/data-design-gate.md` first; acceptance and implementation start after the schema PR merges. Write an event's payload in `docs/architecture/events/<module>.yaml` in the design PR (`docs`); enqueue refuses a non-`docs` PR that changes one.
9. Rule changes follow `coding-standard.md` CS-7.5 and CS-3.9; never invent an approval path.
10. Hand the design PR to lead for the same review and queue checks. No implementation.

Report must include:
- card, PR, head
- the decision, options and trade-offs, the choice and why
- evidence and sources, verified or unverified
- business questions for lead (options, recommendation)
- done-when list for the test engineer
