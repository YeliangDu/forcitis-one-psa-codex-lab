package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountListRow;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AccountRepository.ListPosition;
import com.forcitis.one.platform.identity.domain.GrantChanges;
import com.forcitis.one.platform.identity.domain.GrantClosing;
import com.forcitis.one.platform.identity.domain.GrantHistory;
import com.forcitis.one.platform.identity.domain.GrantHistory.RowPosition;
import com.forcitis.one.platform.identity.domain.GrantRow;
import com.forcitis.one.platform.identity.domain.HeldGrant;
import com.forcitis.one.platform.identity.domain.RevocationCause;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** #1173: the role codes of an account list row are the grants open now; a closed grant is history and shows only in the grant history. */
class AccountListRoleCodesIT extends IdentityIntegrationTest {

    @Autowired
    AccountRepository accounts;

    @Autowired
    GrantHistory history;

    @Test
    @DisplayName("#1173 a role whose grant was closed is not in the role codes of the list row and the role code filter does not find the account by it")
    void closedGrantsAreNotListed() {
        createAccount(9001, "employee", "super_admin");
        UserId user = new UserId(accountOf(9001));
        HeldGrant employee = accounts.openGrants(user).stream().filter(grant -> grant.roleCode().value().equals("employee")).findFirst().orElseThrow();
        accounts.saveGrants(user, new GrantChanges(List.of(), List.of(
                new GrantClosing(employee.id(), RevocationCause.OPERATIONS, null, null, null, new AuditEntryId(UUID.randomUUID())))));

        List<AccountListRow> rows = accounts.list(null, null, null, 10);

        assertThat(rows).singleElement().satisfies(row -> assertThat(row.roleCodes()).containsExactly("super_admin"));
        assertThat(accounts.list(null, new RoleCode("employee"), null, 10)).isEmpty();
        assertThat(accounts.list(null, new RoleCode("super_admin"), null, 10)).hasSize(1);
    }

    @Test
    @DisplayName("#1173 accounts opened at the same moment keep the user id order across page boundaries, whatever order their rows are stored in")
    void accountsOpenedTogetherAreOrderedByUserId() {
        for (long n = 9101; n <= 9105; n++) {
            createAccount(n, "employee");
        }
        List<UUID> byId = new ArrayList<>();
        for (long n = 9101; n <= 9105; n++) {
            byId.add(accountOf(n));
        }
        byId.sort(Comparator.comparing(UUID::toString));
        // Rewriting the rows in descending id order puts them in the reverse of the wanted order in the heap.
        for (UUID id : byId.reversed()) {
            jdbc.sql("update platform_identity.user_accounts set created_at = :at where user_id = :id").param("at", java.time.OffsetDateTime.parse("2026-11-04T00:00:00Z")).param("id", id).update();
        }

        List<UUID> seen = new ArrayList<>();
        ListPosition after = null;
        while (true) {
            List<AccountListRow> page = accounts.list(null, null, after, 2);
            page.forEach(row -> seen.add(row.userId().value()));
            if (page.size() < 2) {
                break;
            }
            after = new ListPosition(Instant.parse("2026-11-04T00:00:00Z"), page.getLast().userId());
        }

        assertThat(seen).containsExactlyElementsOf(byId);
    }

    @Test
    @DisplayName("#1173 grants that started at the same moment keep the grant id descending order across page boundaries, whatever order their rows are stored in")
    void grantsStartedTogetherAreOrderedByGrantIdDescending() {
        pagesThroughGrantsStartedTogether(9201, true);
        pagesThroughGrantsStartedTogether(9202, false);
    }

    private void pagesThroughGrantsStartedTogether(long person, boolean ascendingInsert) {
        List<UUID> roles = jdbc.sql("select role_id from platform_identity.roles").query(UUID.class).list();
        assertThat(roles).hasSizeGreaterThanOrEqualTo(3);
        createAccount(person);
        UserId user = new UserId(accountOf(person));
        Instant start = Instant.parse("2026-11-04T00:00:00Z");
        List<UUID> byIdDescending = new ArrayList<>();
        // Stored in ascending or in descending id order, so the heap order is the reverse of the wanted one or the wanted one itself.
        for (int n = 1; n <= 3; n++) {
            int idNumber = ascendingInsert ? n : 4 - n;
            UUID grantId = UUID.fromString("01900000-0000-7000-8000-0000000" + person + idNumber);
            byIdDescending.add(grantId);
            jdbc.sql("""
                    insert into platform_identity.account_role_grants (account_role_grant_id, user_id, role_id, effective_from_at, grant_channel_kind,
                           grant_audit_entry_id, created_at, updated_at)
                    values (:grant, :user, :role, :start, 'OPERATIONS', uuidv7(), now(), now())
                    """).param("grant", grantId).param("user", user.value()).param("role", roles.get(n - 1))
                    .param("start", java.time.OffsetDateTime.ofInstant(start, java.time.ZoneOffset.UTC)).update();
        }

        List<UUID> seen = new ArrayList<>();
        RowPosition after = null;
        while (true) {
            List<GrantRow> page = history.rowsOf(user, after, 2);
            page.forEach(row -> seen.add(row.grantId().value()));
            if (page.size() < 2) {
                break;
            }
            after = new RowPosition(start, page.getLast().grantId());
        }

        assertThat(seen).containsExactlyElementsOf(byIdDescending.stream().sorted(Comparator.comparing(UUID::toString).reversed()).toList());
    }
}
