package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.InvalidCursorException;
import com.forcitis.one.kernel.InvalidRequestException;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.Uuids;
import com.forcitis.one.organization.workforce.api.EmployeeLabel;
import com.forcitis.one.organization.workforce.api.EmployeeLabels;
import com.forcitis.one.platform.identity.api.RoleCode;
import com.forcitis.one.platform.identity.domain.AccountListRow;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.AccountRepository.ListPosition;
import com.forcitis.one.platform.identity.domain.AccountStatus;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The account list of the account administration page (#1173). user_accounts is GLOBAL (ADR-0026), so there is no data scope to apply. */
@Service
public class ListUserAccounts {

    static final String LIST = "user-accounts";
    private static final String SEPARATOR = "|";

    private final AccountRepository accounts;
    private final EmployeeLabels labels;

    ListUserAccounts(AccountRepository accounts, EmployeeLabels labels) {
        this.accounts = accounts;
        this.labels = labels;
    }

    /** Ordered by opening moment, then user id; the cursor carries both of the account the previous page ended with. An unknown role code matches no grant and so gives an empty page. */
    @Transactional(readOnly = true)
    public CursorPage<AccountListItem> execute(@Nullable String status, @Nullable RoleCode roleCode, PageRequest page) {
        List<AccountListRow> rows = accounts.list(accountStatus(status), roleCode, position(page.cursorValue()), page.limit() + 1);
        List<AccountListRow> shown = rows.stream().limit(page.limit()).toList();
        Map<EmployeeId, EmployeeLabel> named = labels.labelsOf(shown.stream().map(AccountListRow::employeeId).collect(Collectors.toSet()));
        List<AccountListItem> items = shown.stream().map(row -> {
            EmployeeLabel label = named.get(row.employeeId());
            return new AccountListItem(row.userId(), row.status().name(), row.roleCodes(), label == null ? null : label.displayName(),
                    label == null ? null : label.employeeCode());
        }).toList();
        String next = rows.size() > shown.size() ? PageRequest.encode(LIST, shown.getLast().createdAt() + SEPARATOR + shown.getLast().userId().value()) : null;
        return CursorPage.of(items, next);
    }

    private static @Nullable ListPosition position(@Nullable String cursorValue) {
        if (cursorValue == null) {
            return null;
        }
        int cut = cursorValue.indexOf(SEPARATOR);
        try {
            return new ListPosition(CursorMoment.parse(cursorValue.substring(0, cut)), new UserId(Uuids.parseCanonical(cursorValue.substring(cut + 1))));
        } catch (DateTimeParseException | IllegalArgumentException | StringIndexOutOfBoundsException notAPosition) {
            throw new InvalidCursorException("the cursor key is not an account position");
        }
    }

    /** Only the two stored spellings; anything else (lower case, ALL) is a bad request, not an empty list. */
    private static @Nullable AccountStatus accountStatus(@Nullable String status) {
        if (status == null || status.isEmpty()) {
            return null;
        }
        return AccountStatus.ofStoredName(status).orElseThrow(() -> new InvalidRequestException("status must be ENABLED or DISABLED"));
    }
}
