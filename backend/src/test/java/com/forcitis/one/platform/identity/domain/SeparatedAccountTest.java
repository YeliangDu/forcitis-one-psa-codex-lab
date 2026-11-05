package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.TestIds;
import com.forcitis.one.fixture.doubles.TestAccounts;
import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.event.TestEnvelopes;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.api.RoleId;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #1326: separation is a state of the account; a separated account cannot be given a role by any path that goes through the aggregate. */
class SeparatedAccountTest {

    private static final Instant AT = Instant.parse("2026-11-05T01:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-06T01:00:00Z");
    private static final RoleId EMPLOYEE = new RoleId(TestIds.uuid(3));

    private static UserAccount account(Set<RoleId> roles, Instant separatedAt) {
        return UserAccount.restore(TestAccounts.account(1001), TestAccounts.employee(1001), "ENABLED", roles, separatedAt, 5, 5);
    }

    @Test
    @DisplayName("#1326 separating marks every account, one without roles too, and keeps the first moment on a redelivery")
    void separationMarksTheAccountWhateverTheRoles() {
        UserAccount bare = account(Set.of(), null);
        bare.recordSeparation(AT);
        assertThat(bare.separatedAt()).contains(AT);
        assertThat(bare.hasPendingEvents()).as("nothing was taken away, so nothing is announced").isFalse();

        bare.recordSeparation(LATER);
        assertThat(bare.separatedAt()).contains(AT);

        UserAccount holder = account(Set.of(EMPLOYEE), null);
        holder.recordSeparation(AT);
        assertThat(holder.separatedAt()).contains(AT);
        assertThat(holder.roles()).isEmpty();
        assertThat(holder.drainEvents(TestEnvelopes.INSTANCE)).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("AccountRolesReplaced", "AccountSeparated");
    }

    @Test
    @DisplayName("#1326 a separation without a mark (the directory shows the person employed) takes the roles away and records no moment")
    void aSeparationWithoutAMarkLeavesNoMoment() {
        UserAccount holder = account(Set.of(EMPLOYEE), null);
        holder.recordSeparation(null);
        assertThat(holder.separatedAt()).isEmpty();
        assertThat(holder.roles()).isEmpty();
    }

    @Test
    @DisplayName("#1326 a separated account refuses a role it does not hold and still lets roles go")
    void aSeparatedAccountCannotBeGivenARole() {
        UserAccount separated = account(Set.of(), AT);

        assertThatThrownBy(() -> separated.replaceRoles(Set.of(EMPLOYEE)))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.errorCode()).isEqualTo(IdentityError.PLATFORM_IDENTITY_EMPLOYEE_SEPARATED));
        assertThat(separated.roles()).isEmpty();
        assertThat(separated.hasPendingEvents()).isFalse();

        separated.replaceRoles(Set.of());
        assertThat(separated.hasPendingEvents()).isFalse();
    }

    @Test
    @DisplayName("#1326 reinstatement lifts the mark, after which roles may be given again")
    void reinstatementLiftsTheMark() {
        UserAccount separated = account(Set.of(), AT);
        separated.recordReinstated();
        assertThat(separated.separatedAt()).isEmpty();
        separated.replaceRoles(Set.of(EMPLOYEE));
        assertThat(separated.roles()).containsExactly(EMPLOYEE);

        UserAccount employed = account(Set.of(), null);
        employed.recordReinstated();
        assertThat(employed.separatedAt()).isEmpty();
    }
}
