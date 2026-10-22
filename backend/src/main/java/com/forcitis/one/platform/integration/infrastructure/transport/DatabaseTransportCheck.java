package com.forcitis.one.platform.integration.infrastructure.transport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Checks the JDBC URLs of every database connection against {@link DatabaseTransportRequirement}. PostgreSQL carries sslmode and
 * sslrootcert in the URL; SQL Server carries encrypt, trustServerCertificate and trustStore (the driver has no PEM CA path).
 */
final class DatabaseTransportCheck {
    private static final Map<String, String> SOURCES = sources();

    private DatabaseTransportCheck() {
    }

    private static Map<String, String> sources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("primary database", "spring.datasource.url");
        sources.put("migration connection", "spring.flyway.url");
        sources.put("A8 views", "forcitis.platform.integration.seeyon.views.jdbc-url");
        sources.put("legacy short names", "forcitis.platform.integration.legacy.jdbc-url");
        return sources;
    }

    /** {@code property} returns the configured value of a key, or null. A source that is switched off or has no URL is not checked. */
    static List<String> problems(DatabaseTransportRequirement required, Function<String, String> property) {
        List<String> problems = new ArrayList<>();
        if (required == DatabaseTransportRequirement.NONE) {
            return problems;
        }
        SOURCES.forEach((name, key) -> {
            String url = property.apply(key);
            if (url == null || url.isBlank() || switchedOff(name, property)) {
                return;
            }
            String problem = url.startsWith("jdbc:postgresql:") ? postgres(required, url)
                    : url.startsWith("jdbc:sqlserver:") ? sqlServer(required, url)
                    : "is neither a PostgreSQL nor a SQL Server URL, so its transport cannot be checked";
            if (problem != null) {
                problems.add(name + " (" + key + "): " + problem);
            }
        });
        if (property.apply("spring.datasource.url") == null || property.apply("spring.datasource.url").isBlank()) {
            problems.add("primary database (spring.datasource.url): not configured");
        }
        return problems;
    }

    private static boolean switchedOff(String name, Function<String, String> property) {
        String enabledKey = switch (name) {
            case "A8 views" -> "forcitis.platform.integration.seeyon.views.enabled";
            case "legacy short names" -> "forcitis.platform.integration.legacy.enabled";
            default -> null;
        };
        return enabledKey != null && !"true".equalsIgnoreCase(property.apply(enabledKey));
    }

    private static String postgres(DatabaseTransportRequirement required, String url) {
        Map<String, String> parameters = urlParameters(url);
        String mode = parameters.getOrDefault("sslmode", "prefer").toLowerCase(Locale.ROOT);
        if (required == DatabaseTransportRequirement.ENCRYPTED) {
            return Set.of("require", "verify-ca", "verify-full").contains(mode) ? null : "sslmode=" + mode + " does not encrypt; dev needs at least require";
        }
        if (!mode.equals("verify-full")) {
            return "sslmode=" + mode + "; production needs verify-full";
        }
        return unreadable(parameters.get("sslrootcert"), "sslrootcert");
    }

    private static String sqlServer(DatabaseTransportRequirement required, String url) {
        Map<String, String> parameters = sqlServerParameters(url);
        String encrypt = parameters.getOrDefault("encrypt", "false").toLowerCase(Locale.ROOT);
        if (!Set.of("true", "strict").contains(encrypt)) {
            return "encrypt=" + encrypt + " does not encrypt; the connection needs encrypt=true";
        }
        if (required == DatabaseTransportRequirement.ENCRYPTED) {
            return null;
        }
        if ("true".equalsIgnoreCase(parameters.get("trustservercertificate"))) {
            return "trustServerCertificate=true trusts any certificate; production needs false";
        }
        return unreadable(parameters.get("truststore"), "trustStore");
    }

    private static String unreadable(String path, String parameter) {
        if (path == null || path.isBlank()) {
            return "no " + parameter + " (path of the CA certificate); production verifies the server against it";
        }
        Path file = Path.of(path);
        return Files.isRegularFile(file) && Files.isReadable(file) ? null : parameter + "=" + path + " is not a readable file";
    }

    private static Map<String, String> urlParameters(String url) {
        Map<String, String> parameters = new LinkedHashMap<>();
        int query = url.indexOf('?');
        if (query >= 0) {
            for (String pair : url.substring(query + 1).split("&")) {
                int equals = pair.indexOf('=');
                if (equals > 0) {
                    parameters.put(pair.substring(0, equals).toLowerCase(Locale.ROOT), pair.substring(equals + 1));
                }
            }
        }
        return parameters;
    }

    private static Map<String, String> sqlServerParameters(String url) {
        Map<String, String> parameters = new LinkedHashMap<>();
        String[] parts = url.split(";");
        for (int i = 1; i < parts.length; i++) {
            int equals = parts[i].indexOf('=');
            if (equals > 0) {
                parameters.put(parts[i].substring(0, equals).trim().toLowerCase(Locale.ROOT), parts[i].substring(equals + 1).trim());
            }
        }
        return parameters;
    }
}
