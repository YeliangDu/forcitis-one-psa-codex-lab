复审结论：通过
commit：5c8d134bf6cd149c1f020d5ae177b52418491e36
复审者：sonnet-首轮-pin

patch-id：f0685645ff99a8e7fd58739c33e5cf73c792ae40

Impact: test-only change to SystemRoleDeclarationIT, which pins org_data_admin (revision 4, +permission-adjustment:read/update) and security_auditor (revision 2, +permission-adjustment:read).

## Must-fix
None.

## Suggestions
- The failsafe-reports directory holds a stale PermissionAdjustmentActionApiIT report (1 failure, dated 16:12, before this run; it was not run in this review and is not part of this diff). Worth a separate look by the author, outside this commit.

## Checked and fine
- Head is 5c8d134bf6cd; it is a single commit and the diff is against its parent. The only file changed is SystemRoleDeclarationIT.java (+6/-5), and only the three pin locations are touched: the org_data_admin code list, the security_auditor code list, and the revision expression with its message and javadoc. No assertion was loosened or deleted. The new codes are added to the expected lists in sorted position. "platform.identity" sorts before "platform.integration", and after "platform.audit" for security_auditor.
- Trailer `Acceptance-Ruling: RULING-LEAD-20261103-16` is present and matches the ruling id given in the input. The scope is the two role pins (revision plus permission codes) and nothing else.
- Main code SystemRoleDeclaration.java:39-51 declares org_data_admin at revision 4 with PLATFORM_IDENTITY_PERMISSION_ADJUSTMENT_READ and _UPDATE, and security_auditor at revision 2 with _READ only. These equal the new pins. business_admin stays at 2, and the other roles stay at 1.
- ADR-0044.md:98 says update goes only to the org data admin and the security auditor holds read only. The :98 and f-1.md:646 text both say read is granted to org_data_admin and security_auditor and update only to org_data_admin. This matches the pins.
- SystemRoleRaisedRevisionIT has no permission-adjustment or hard-coded production-revision references; it uses its own RaisedDeclarationLauncher, so it needs no change.
- Ran both ITs with the given command. Failsafe reports: SystemRoleDeclarationIT Tests run 7, Failures 0, Errors 0; SystemRoleRaisedRevisionIT Tests run 1, Failures 0, Errors 0.
