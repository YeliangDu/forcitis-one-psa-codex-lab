package com.forcitis.one.organization.workforce.domain;

import com.forcitis.one.kernel.EmployeeId;
import com.forcitis.one.kernel.LegalEntityId;
import com.forcitis.one.organization.workforce.api.OrgStructureCommandId;
import com.forcitis.one.organization.workforce.api.OrgUnitId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The names the countersign read paths show next to the ids (#910 amendment B); read-only, every method joins the caller's transaction. */
public interface CountersignReadQueries {

    /** An employee as the read path shows them: the code and the display name, nothing else. */
    record Person(String employeeCode, String displayName) {
    }

    /** One stored basis row with the id the page cursor carries. */
    record BasisEntry(UUID rowKey, OrgCountersignBasis basis) {
    }

    Map<LegalEntityId, String> legalEntityNames(Collection<LegalEntityId> ids);

    Map<EmployeeId, Person> employees(Collection<EmployeeId> ids);

    Optional<String> orgUnitName(OrgUnitId orgUnit);

    /** The command's basis rows in the order they were written, the ones after {@code after} (a row id of an earlier page), at most {@code limit}. */
    List<BasisEntry> basisPage(OrgStructureCommandId command, @Nullable UUID after, int limit);
}
