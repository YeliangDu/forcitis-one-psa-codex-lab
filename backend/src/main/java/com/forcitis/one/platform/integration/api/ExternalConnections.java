package com.forcitis.one.platform.integration.api;

import javax.sql.DataSource;

/** The only way to reach an external database: every statement, network read and lock wait on the returned pool is bounded by the limit of the call kind. */
public interface ExternalConnections {

    DataSource open(String poolName, String jdbcUrl, String username, String password, ExternalCallKind kind);
}
