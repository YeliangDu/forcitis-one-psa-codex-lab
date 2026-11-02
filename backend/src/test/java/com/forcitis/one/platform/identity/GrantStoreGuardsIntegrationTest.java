package com.forcitis.one.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.api.AuditEntryId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.GrantChanges;
import com.forcitis.one.platform.identity.domain.GrantClosing;
import com.forcitis.one.platform.identity.domain.HeldGrant;
import com.forcitis.one.platform.identity.domain.RevocationCause;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GrantStoreGuardsIntegrationTest extends IdentityIntegrationTest {

    @Autowired
    AccountRepository accounts;

    @Test
    @DisplayName("#732 closing a grant that is not open any more is a conflict, not a silent no-op")
    void closingAClosedGrantConflicts() {
        createAccount(1001, "employee");
        UserId user = new UserId(accountOf(1001));
        HeldGrant held = accounts.openGrants(user).getFirst();
        accounts.saveGrants(user, new GrantChanges(List.of(), List.of(
                new GrantClosing(held.id(), RevocationCause.OPERATIONS, null, null, null, new AuditEntryId(UUID.randomUUID())))));
        assertThat(accounts.openGrants(user)).isEmpty();

        assertThatThrownBy(() -> accounts.saveGrants(user, new GrantChanges(List.of(), List.of(
                new GrantClosing(held.id(), RevocationCause.OPERATIONS, null, null, null, new AuditEntryId(UUID.randomUUID()))))))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_CONCURRENT_UPDATE));
    }
}
