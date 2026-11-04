package com.forcitis.one.platform.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.GrantHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("#1173 a forged cursor of the account list and the grant history is an invalid cursor (400), never a database or conversion failure (500)")
class ListCursorsTest {
    static final String ID = "01900000-0000-7000-8000-00000000c001";

    private final AccountRepository accounts = Mockito.mock(AccountRepository.class);
    private final ListUserAccounts list = new ListUserAccounts(accounts, Mockito.mock(EmployeeLabels.class));
    private final ListAccountRoleGrants grants = new ListAccountRoleGrants(accounts, Mockito.mock(GrantHistory.class));

    private static PageRequest page(String listName, String key) {
        return PageRequest.of(2, PageRequest.encode(listName, key), listName);
    }

    @Test
    @DisplayName("a moment after year 9999, before 1970, or beyond what an Instant converts is refused in both cursors")
    void momentsOutsideTheStoredRangeAreRefused() {
        UserId user = new UserId(UUID.fromString(ID));
        Mockito.when(accounts.findEmployee(user)).thenReturn(Optional.of(new com.forcitis.one.kernel.EmployeeId(UUID.randomUUID())));
        for (String moment : List.of("+300000-01-01T00:00:00Z", "+1000000000-12-31T23:59:59.999999999Z", "1969-12-31T23:59:59Z", "-300000-01-01T00:00:00Z")) {
            assertThatThrownBy(() -> list.execute(null, null, page("user-accounts", moment + "|" + ID))).as(moment).isInstanceOf(InvalidCursorException.class);
            assertThatThrownBy(() -> grants.execute(user, page("role-grants", moment + "|" + ID))).as(moment).isInstanceOf(InvalidCursorException.class);
        }
    }

    @Test
    @DisplayName("a moment inside the range is a position: the repository is asked for the rows after it")
    void aMomentInsideTheRangeIsAccepted() {
        Mockito.when(accounts.list(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.anyInt())).thenReturn(List.of());

        assertThat(list.execute(null, null, page("user-accounts", "2026-11-04T00:00:00Z|" + ID)).items()).isEmpty();
    }
}
