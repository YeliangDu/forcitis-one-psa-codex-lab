package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.platform.identity.api.AccountRoleGrantId;
import com.forcitis.one.platform.identity.api.IdentityError;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.GrantHistory;
import com.forcitis.one.platform.identity.domain.GrantHistory.RowPosition;
import com.forcitis.one.platform.identity.domain.GrantRow;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every grant row of one account, open and closed, newest first (#1173). Nothing but the stored grant facts: ids of people, never their names. */
@Service
public class ListAccountRoleGrants {

    static final String LIST = "role-grants";
    private static final String SEPARATOR = "|";

    private final AccountRepository accounts;
    private final GrantHistory history;

    ListAccountRoleGrants(AccountRepository accounts, GrantHistory history) {
        this.accounts = accounts;
        this.history = history;
    }

    /** The cursor carries the start and the id of the row the previous page ended with, which together order the rows. */
    @Transactional(readOnly = true)
    public CursorPage<AccountGrantItem> execute(UserId user, PageRequest page) {
        if (accounts.findEmployee(user).isEmpty()) {
            throw IdentityError.PLATFORM_IDENTITY_ACCOUNT_NOT_FOUND.refusal("no account " + user.value());
        }
        List<GrantRow> rows = history.rowsOf(user, position(page.cursorValue()), page.limit() + 1);
        List<GrantRow> shown = rows.stream().limit(page.limit()).toList();
        GrantRow last = shown.isEmpty() ? null : shown.getLast();
        String next = rows.size() > shown.size() && last != null
                ? PageRequest.encode(LIST, last.effectiveFrom() + SEPARATOR + last.grantId().value()) : null;
        List<AccountGrantItem> items = shown.stream().map(row -> new AccountGrantItem(row.roleCode(), row.channel(), row.effectiveFrom(), row.effectiveTo(),
                row.revocationCause(), row.grantedBy(), row.revokedBy())).toList();
        return CursorPage.of(items, next);
    }

    private static @Nullable RowPosition position(@Nullable String cursorValue) {
        if (cursorValue == null) {
            return null;
        }
        int cut = cursorValue.indexOf(SEPARATOR);
        try {
            return new RowPosition(CursorMoment.parse(cursorValue.substring(0, cut)), new AccountRoleGrantId(Uuids.parseCanonical(cursorValue.substring(cut + 1))));
        } catch (DateTimeParseException | IllegalArgumentException | StringIndexOutOfBoundsException notAPosition) {
            throw new InvalidCursorException("the cursor key is not a grant position");
        }
    }
}
