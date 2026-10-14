package com.forcitis.one.organization.workforce.infrastructure.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * forcitis.organization.workforce.legacy.*: the read-only connection to the old system's database, used only for the
 * daily short-name refresh (department id and short_name). Values come from environment variables.
 */
@ConfigurationProperties("forcitis.organization.workforce.legacy")
record LegacyProperties(boolean enabled, String jdbcUrl, String username, String password, boolean validateOnStartup) {
}
