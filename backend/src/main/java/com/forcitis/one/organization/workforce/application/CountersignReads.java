package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.AccountEmployees;
import com.forcitis.one.kernel.AccountRoles;
import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignDetail;
import com.forcitis.one.organization.workforce.api.AttributionCountersignDetail.AttributionCountersignRow;
import com.forcitis.one.organization.workforce.api.AttributionCountersignId;
import com.forcitis.one.organization.workforce.api.AttributionCountersignStatus;
import com.forcitis.one.organization.workforce.api.AttributionCountersignSummary;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries.Row;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignQueries.Visible;
import com.forcitis.one.organization.workforce.domain.CountersignStatus;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * What a decider reads of the countersign requests (amendment B of #895): the requests of the legal entities they head today, and from escalation level 2 on those of any
 * entity when they are a business administrator. An empty scope is an empty page; a request out of scope is the same refusal as one that is not there.
 */
@Component
class CountersignReads {
    static final String LIST = "attribution-countersigns";

    private final AttributionCountersignQueries queries;
    private final AccountRoles roles;
    private final AccountEmployees accounts;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final BusinessZone zone;

    CountersignReads(AttributionCountersignQueries queries, AccountRoles roles, AccountEmployees accounts, CurrentUser currentUser, Clock clock, BusinessZone zone) {
        this.queries = queries;
        this.roles = roles;
        this.accounts = accounts;
        this.currentUser = currentUser;
        this.clock = clock;
        this.zone = zone;
    }

    CursorPage<AttributionCountersignSummary> list(@Nullable AttributionCountersignStatus status, PageRequest page) {
        CountersignStatus wanted = CountersignStatus.requested(status);
        List<Row> found = queries.list(visible(), wanted, page.cursorUuid(), page.limit() + 1);
        boolean more = found.size() > page.limit();
        List<Row> rows = more ? found.subList(0, page.limit()) : found;
        Names names = names(rows);
        List<AttributionCountersignSummary> items = rows.stream().map(row -> summary(row, names)).toList();
        return CursorPage.of(items, more ? PageRequest.encode(LIST, rows.getLast().id().toString()) : null);
    }

    AttributionCountersignDetail get(AttributionCountersignId id) {
        Row row = queries.find(visible(), id).orElseThrow(AttributionCountersignService::notFound);
        List<Row> siblings = queries.sameCommand(id);
        Names names = names(siblings);
        AttributionCountersignSummary head = summary(row, names);
        return new AttributionCountersignDetail(head.attributionCountersignId(), head.personnelCommandId(), head.commandKind(), head.commandStatus(), head.effectiveDate(),
                head.employeeId(), head.employeeDisplayName(), head.employeeNumber(), head.requestKind(), head.requestSequenceNumber(), head.status(), head.legalEntityId(),
                head.legalEntityName(), head.previousLegalEntityId(), head.previousLegalEntityName(), head.newLegalEntityId(), head.newLegalEntityName(), head.requestedAt(),
                head.requestedByDisplayName(), head.dueDate(), head.escalationLevel(), row.previousOrgUnitId(), row.previousOrgUnitName(),
                row.newOrgUnitId(), row.newOrgUnitName(), row.requestReason(), row.decisionReason(),
                siblings.stream().map(sibling -> row(sibling, names)).toList());
    }

    private Visible visible() {
        UserId user = currentUser.userId();
        return new Visible(currentUser.employeeId(), roles.holds(user, CountersignDeciders.ESCALATED_ROLE), zone.today(clock));
    }

    private static AttributionCountersignSummary summary(Row row, Names names) {
        return new AttributionCountersignSummary(new AttributionCountersignId(row.id()), row.personnelCommandId(), row.commandKind(),
                row.commandState(), row.effectiveDate(), row.employeeId(), row.employeeDisplayName(), row.employeeNumber(), row.requestKind(),
                row.requestSequenceNumber(), row.status().toApi(), row.legalEntityId(), row.legalEntityName(),
                row.previousLegalEntityId(), row.previousLegalEntityName(), row.newLegalEntityId(), row.newLegalEntityName(),
                row.requestedAt(), names.ofAccount(row.requestedBy()), row.dueDate(), row.escalationLevel());
    }

    private static AttributionCountersignRow row(Row row, Names names) {
        return new AttributionCountersignRow(new AttributionCountersignId(row.id()), row.requestKind(), row.requestSequenceNumber(), row.legalEntityId(),
                row.legalEntityName(), row.status().toApi(), row.requestedAt(), names.ofAccount(row.requestedBy()), row.dueDate(),
                row.escalationLevel(), row.decidedAt(), row.decidedBy() == null ? null : names.ofAccount(row.decidedBy()), row.decisionReason());
    }

    /** The accounts behind the requesters and deciders of the rows, as the display names of the employees they belong to. */
    private Names names(List<Row> rows) {
        Set<UUID> accountIds = new HashSet<>();
        for (Row row : rows) {
            accountIds.add(row.requestedBy());
            if (row.decidedBy() != null) {
                accountIds.add(row.decidedBy());
            }
        }
        Map<UUID, UUID> employeeOfAccount = new HashMap<>();
        for (UUID account : accountIds) {
            Optional<EmployeeId> employee = accounts.employeeOf(new UserId(account));
            employee.ifPresent(found -> employeeOfAccount.put(account, found.value()));
        }
        return new Names(employeeOfAccount, queries.employeeNames(Set.copyOf(employeeOfAccount.values())));
    }

    private record Names(Map<UUID, UUID> employeeOfAccount, Map<UUID, String> displayNames) {
        @Nullable String ofAccount(UUID account) {
            UUID employee = employeeOfAccount.get(account);
            return employee == null ? null : displayNames.get(employee);
        }
    }
}
