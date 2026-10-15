package com.forcitis.one.platform.integration.infrastructure.legacy;

import com.forcitis.one.kernel.ExternalSystemUnavailableException;
import com.forcitis.one.platform.integration.api.OrgShortNames;
import com.forcitis.one.platform.integration.api.SourceUnitId;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Reads two columns (id, short_name) of one dedicated view; never any token table (I-1). */
final class LegacyOrgNames implements OrgShortNames {

    private final JdbcClient jdbc;
    private final String viewName;
    private final Runnable recheckBeforeRead;

    LegacyOrgNames(JdbcClient jdbc, String viewName, Runnable recheckBeforeRead) {
        this.jdbc = jdbc;
        this.viewName = viewName;
        this.recheckBeforeRead = recheckBeforeRead;
    }

    /** Only the short-name refresh reads here, so the login is checked again before each refresh (ADR-0037 item 4.4). */
    @Override
    public Map<SourceUnitId, String> readShortNames() {
        recheckBeforeRead.run();
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
            throw new ExternalSystemUnavailableException("old system short names cannot be read (" + sqlFailure(unreachable) + ")");
        }
    }

    /** ADR-0037 item 3.5: SQL Server messages can carry column values, so only the type, the error number and the SQLState are told. */
    static String sqlFailure(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return failure.getClass().getSimpleName() + ", error " + sql.getErrorCode() + ", SQLState " + sql.getSQLState();
            }
        }
        return failure.getClass().getSimpleName() + ", no SQL error";
    }
}
