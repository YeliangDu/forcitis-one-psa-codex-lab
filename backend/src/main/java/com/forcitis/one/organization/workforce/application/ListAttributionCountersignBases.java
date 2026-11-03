package com.forcitis.one.organization.workforce.application;

import com.forcitis.one.kernel.BusinessZone;
import com.forcitis.one.kernel.CallerPermissions;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.BasisLine;
import com.forcitis.one.organization.workforce.application.CountersignReadViews.Person;
import com.forcitis.one.organization.workforce.domain.AttributionCountersign;
import com.forcitis.one.organization.workforce.domain.AttributionCountersignRepository;
import com.forcitis.one.organization.workforce.domain.CountersignReadQueries;
import com.forcitis.one.organization.workforce.domain.CountersignRequestKind;
import com.forcitis.one.organization.workforce.domain.OrgStructureCommandRepository;
import com.forcitis.one.platform.audit.api.AuditSubject;
import com.forcitis.one.platform.audit.api.AuditSubjectKind;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The people a command changes the cost legal entity of, before and after; finance heads of an entity it asks and the org data administrator read it (#910 amendment B). */
@Service
public class ListAttributionCountersignBases {
    static final String LIST = "attribution-countersign-bases";

    private final CountersignReadAccess access;
    private final CountersignReadQueries names;

    ListAttributionCountersignBases(CountersignReadAccess access, CountersignReadQueries names) {
        this.access = access;
        this.names = names;
    }

    @Transactional(readOnly = true)
    public CursorPage<BasisLine> execute(OrgStructureCommandId command, @Nullable Integer limit, @Nullable String cursor) {
        PageRequest page = PageRequest.of(limit, cursor, LIST);
        access.requireBasisReadable(command);
        List<CountersignReadQueries.BasisEntry> entries = names.basisPage(command, page.cursorUuid(), page.limit() + 1);
        boolean more = entries.size() > page.limit();
        List<CountersignReadQueries.BasisEntry> shown = more ? entries.subList(0, page.limit()) : entries;
        Map<EmployeeId, CountersignReadQueries.Person> people = names.employees(shown.stream().map(e -> e.basis().employeeId()).toList());
        Map<LegalEntityId, String> entities = names.legalEntityNames(shown.stream().flatMap(e -> Stream.of(e.basis().previous(), e.basis().next())).toList());
        List<BasisLine> lines = new ArrayList<>();
        for (CountersignReadQueries.BasisEntry entry : shown) {
            CountersignReadQueries.Person person = people.get(entry.basis().employeeId());
            lines.add(new BasisLine(entry.basis().employeeId().value(), person == null ? "" : person.displayName(), person == null ? "" : person.employeeCode(),
                    entry.basis().previous().value(), entities.get(entry.basis().previous()), entry.basis().next().value(), entities.get(entry.basis().next())));
        }
        return CursorPage.of(lines, more ? PageRequest.encode(LIST, shown.getLast().rowKey().toString()) : null);
    }
}
