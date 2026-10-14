import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Read-only query runner for the legacy database (C-6). Run by legacy_probe_run.sh on the legacy application server.
 * Usage: java -cp mssql-jdbc.jar LegacyReadOnlyProbe.java <datasource yaml> <sql file>
 * Prints each result set as tab-separated rows; never prints connection settings.
 */
public class LegacyReadOnlyProbe {

    private static final int MAX_ROWS = 3000;
    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE|DENY|INTO|OPENROWSET|OPENQUERY|BACKUP|RESTORE|DBCC|SHUTDOWN|KILL)\\b",
            Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: LegacyReadOnlyProbe <datasource yaml> <sql file>");
        }
        String[] master = masterDatasource(Files.readAllLines(Path.of(args[0])));
        List<String> statements = statements(Files.readString(Path.of(args[1])));

        try (Connection connection = DriverManager.getConnection(master[0], master[1], master[2])) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            int index = 0;
            for (String sql : statements) {
                index++;
                System.out.println("## " + index);
                try (Statement statement = connection.createStatement()) {
                    statement.setMaxRows(MAX_ROWS);
                    statement.setQueryTimeout(120);
                    try (ResultSet rows = statement.executeQuery(sql)) {
                        print(rows);
                    }
                }
            }
        }
    }

    static List<String> statements(String text) {
        String withoutComments = text.replaceAll("(?m)--.*$", "").replaceAll("(?s)/\\*.*?\\*/", "");
        List<String> result = new ArrayList<>();
        for (String part : withoutComments.split(";")) {
            String sql = part.strip();
            if (sql.isEmpty()) {
                continue;
            }
            String head = sql.toUpperCase(Locale.ROOT);
            if (!(head.startsWith("SELECT") || head.startsWith("WITH"))) {
                throw new IllegalArgumentException("only SELECT statements are allowed: " + firstLine(sql));
            }
            if (FORBIDDEN.matcher(sql).find()) {
                throw new IllegalArgumentException("statement contains a forbidden keyword: " + firstLine(sql));
            }
            result.add(sql);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("no statements");
        }
        return result;
    }

    /** Returns url, username, password of the {@code master:} block; fails rather than guessing. */
    static String[] masterDatasource(List<String> yaml) {
        String url = null, user = null, password = null, name = null;
        int masterIndent = -1;
        for (String line : yaml) {
            if (line.isBlank() || line.strip().startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            String trimmed = line.strip();
            if (masterIndent < 0) {
                if (trimmed.equals("master:")) {
                    masterIndent = indent;
                }
                continue;
            }
            if (indent <= masterIndent) {
                break;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).strip();
            String value = unquote(trimmed.substring(colon + 1).strip());
            switch (key) {
                case "url": url = value; break;
                case "username": user = value; break;
                case "password": password = value; break;
                case "name": name = value; break;
                default: break;
            }
        }
        // The legacy configuration builds the URL from the sibling "name" key: ${spring.datasource.dynamic.datasource.master.name}.
        if (url != null && name != null) {
            url = url.replace("${spring.datasource.dynamic.datasource.master.name}", name);
        }
        if (url == null || user == null || password == null || url.contains("${") || user.contains("${") || password.contains("${")) {
            throw new IllegalStateException("master datasource is incomplete or uses placeholders");
        }
        return new String[] {url, user, password};
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static void print(ResultSet rows) throws Exception {
        ResultSetMetaData meta = rows.getMetaData();
        StringBuilder header = new StringBuilder();
        for (int c = 1; c <= meta.getColumnCount(); c++) {
            header.append(c > 1 ? "\t" : "").append(meta.getColumnLabel(c));
        }
        System.out.println(header);
        int count = 0;
        while (rows.next()) {
            StringBuilder row = new StringBuilder();
            for (int c = 1; c <= meta.getColumnCount(); c++) {
                String value = rows.getString(c);
                row.append(c > 1 ? "\t" : "").append(value == null ? "NULL" : value.replace('\t', ' ').replace('\n', ' '));
            }
            System.out.println(row);
            count++;
        }
        System.out.println("# rows=" + count + (count == MAX_ROWS ? " (truncated at max rows)" : ""));
    }

    private static String firstLine(String sql) {
        int newline = sql.indexOf('\n');
        return newline < 0 ? sql : sql.substring(0, newline);
    }
}
