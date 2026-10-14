package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Reads org unit short names from the dedicated read-only view of the old system, two columns only (id, short_name).
 * It touches nothing else of the old database - in particular never any token table (SECURITY_INVARIANTS I-1); the
 * start-up check proves the account can SELECT exactly this one object.
 */
final class LegacyOrgNames implements OrgShortNames {

    private final JdbcClient jdbc;
    private final String viewName;

    LegacyOrgNames(JdbcClient jdbc, String viewName) {
        this.jdbc = jdbc;
        this.viewName = viewName;
    }

    @Override
    public Map<SourceUnitId, String> readShortNames() {
        try {
            Map<SourceUnitId, String> names = new LinkedHashMap<>();
            jdbc.sql("SELECT id, short_name FROM " + viewName).query((rs, n) -> {
                long id = rs.getLong("id");
                String shortName = rs.getString("short_name");
                if (id != 0) {
                    names.put(new SourceUnitId(id), shortName == null || shortName.isBlank() ? null : shortName.trim());
                }
                return null;
            }).list();
            return names;
        } catch (DataAccessException unreachable) {
            throw new ExternalSystemUnavailableException(
                    "old system short names cannot be read (" + unreachable.getClass().getSimpleName() + ")", unreachable);
        }
    }
}
