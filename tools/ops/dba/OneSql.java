import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Runs a sqlcmd-style script (":setvar", "$(VAR)", "GO") against the legacy SQL Server instance.
 * usage: java -cp mssql-jdbc.jar OneSql.java ro <database> <script> [VAR=VALUE ...]   |   java OneSql.java --self-test
 *   login: LOGIN_USER + LOGIN_SECRET_ENC + LOGIN_KEY (the evidence account's password as RSA-OAEP ciphertext); nothing else
 *   ENC_PUBKEY: PEM public key; any result column named one_time_secret is printed only as RSA-OAEP(SHA-256) ciphertext
 *   read only (#752): the only mode is ro; a script with a DDL/DML keyword is refused before connecting, and the
 *   batches run inside a transaction that is always rolled back. The rw and deny modes and the DBA login from stdin are gone.
 */
public class OneSql {
    static final String HOST = "rm-bp1690ep53t9778dp.sqlserver.rds.aliyuncs.com";
    // matched on the code only (comments and string literals removed by codeOnly): anything that writes, runs other code, ends the transaction or reaches outside the server
    static final Pattern WRITE = Pattern.compile("(?<![A-Za-z_@#$])(CREATE|ALTER|DROP|GRANT|DENY|REVOKE|INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE|TRUNCATE|BACKUP|RESTORE|SHUTDOWN|KILL|DBCC"
            + "|COMMIT|ROLLBACK|SAVE|BEGIN\\s+(TRAN|TRANSACTION|DISTRIBUTED)|WRITETEXT|UPDATETEXT|BULK|OPENROWSET|OPENDATASOURCE|OPENQUERY|RECONFIGURE|USE"
            + "|(sp|xp)_\\w+|INTO\\s+(?!#))\\b", Pattern.CASE_INSENSITIVE);

    /** A token that starts with a digit (not inside an identifier) and goes on with a letter, @, # or $: SQL Server lexes number and keyword apart where the keyword regex sees one word. */
    static final Pattern NUMBER_GLUED = Pattern.compile("(?<![\\w@#$])\\d+[A-Za-z_@#$]");

    public static void main(String[] a) throws Exception {
        if (a.length >= 1 && a[0].equals("--self-test")) {
            selfTest(a.length > 1 ? Path.of(a[1]) : null);
            return;
        }
        if (a.length < 3 || !a[0].equals("ro")) throw new IllegalArgumentException("usage: OneSql.java ro <database> <script> [VAR=VALUE ...] (read only; no other mode)");
        String db = a[1];
        String script = Files.readString(Path.of(a[2]), StandardCharsets.UTF_8);
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 3; i < a.length; i++) {
            int eq = a[i].indexOf('=');
            vars.put(a[i].substring(0, eq), a[i].substring(eq + 1));
        }
        List<String> batches = readOnlyBatches(script, vars);
        if (System.getenv("LOGIN_USER") == null) throw new IllegalStateException("LOGIN_USER, LOGIN_SECRET_ENC and LOGIN_KEY are required (the evidence account)");
        String user = System.getenv("LOGIN_USER");
        String secret = decrypt(Files.readString(Path.of(System.getenv("LOGIN_SECRET_ENC"))).trim(), System.getenv("LOGIN_KEY"));
        String url = "jdbc:sqlserver://" + HOST + ":1433;databaseName=" + db
                + ";encrypt=true;trustServerCertificate=true;applicationName=forcitis-one-ops;loginTimeout=15";
        PublicKey pub = System.getenv("ENC_PUBKEY") == null ? null : publicKey(System.getenv("ENC_PUBKEY"));
        try (Connection c = DriverManager.getConnection(url, user, secret)) {
            secret = null;
            System.out.println("connected as " + user + " db=" + db + " mode=ro");
            System.out.println("DONE batches=" + runRolledBack(c, batches, pub));
        }
    }

    /** Runs the batches with auto-commit off and rolls back whatever happened, also when a batch fails. Returns the number of batches run. */
    static int runRolledBack(Connection c, List<String> batches, PublicKey pub) throws Exception {
        c.setAutoCommit(false);
        int n = 0;
        try {
            for (String b : batches) {
                n++;
                String head = firstComment(b);
                System.out.println("---- batch " + n + (head.isEmpty() ? "" : " " + head));
                run(c, b, pub);
            }
        } finally {
            c.rollback();
        }
        return n;
    }

    /** The batch with comments blanked and string literals emptied, scanned in one pass so that '--' inside a string is no comment and a quote inside a comment no string. */
    static String codeOnly(String s) {
        StringBuilder out = new StringBuilder();
        int i = 0, n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '-' && i + 1 < n && s.charAt(i + 1) == '-') {
                while (i < n && s.charAt(i) != '\n') i++;
                out.append(' ');
            } else if (ch == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else if (ch == '\'') {
                i++;
                while (i < n && !(s.charAt(i) == '\'' && (i + 1 >= n || s.charAt(i + 1) != '\''))) i += s.charAt(i) == '\'' ? 2 : 1;
                i++;
                out.append("''");
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    /** The script's batches, refused as a whole when any batch carries a write keyword (comments ignored). */
    static List<String> readOnlyBatches(String script, Map<String, String> vars) {
        List<String> batches = parse(script, vars);
        for (String b : batches) {
            Matcher m = WRITE.matcher(codeOnly(b));
            if (m.find()) throw new IllegalStateException("read-only script contains write keyword: " + m.group());
            if (NUMBER_GLUED.matcher(codeOnly(b)).find()) throw new IllegalStateException("read-only script contains a number glued to letters (1e5, 0xAB, 1DELETE): SQL Server reads a number and a keyword there");
        }
        return batches;
    }

    /** No database: a write statement is refused, a read passes, any mode but ro is refused before the script is read, the batches always run with
     * auto-commit off and end in a rollback (also when one fails), and every evidence script in the given folder (the B-5 evidence/ folder) is accepted. */
    static void selfTest(Path evidence) throws Exception {
        Map<String, String> none = new LinkedHashMap<>();
        check(readOnlyBatches("SELECT name FROM sys.tables\nGO\n-- DELETE in a comment is fine\nSELECT 1\n", none).size() == 2, "reads pass, comments are not keywords");
        check(readOnlyBatches("SELECT N'EXECUTE', 'it''s -- not a comment' AS a INTO #tmp FROM t\nSELECT p.permission_name FROM #tmp p WHERE p.x IN (N'DELETE')", none).size() == 1,
                "keywords inside string literals and SELECT INTO a temporary table are reads");
        for (String write : new String[] {"DELETE FROM t", "update t set a = 1", "INSERT INTO t VALUES (1)", "DROP TABLE t", "GRANT SELECT ON t TO u",
                "ALTER LOGIN x WITH PASSWORD = 'p'", "EXEC sp_who", "TRUNCATE TABLE t", "MERGE t USING s ON 1 = 1 WHEN MATCHED THEN DELETE;",
                "SELECT 1\nGO\nCREATE TABLE t (a int)", "SELECT '--'; DELETE FROM t", "SELECT 'a'' -- ''b'; DELETE FROM t", "EXECUTE('DEL' + 'ETE FROM t')",
                "sp_executesql N'DELETE FROM t'", "SELECT * INTO dbo.copy FROM t", "WRITETEXT t.c @p 'x'", "UPDATETEXT t.c @p 0 NULL 'x'", "COMMIT",
                "SELECT 1; COMMIT TRANSACTION", "BEGIN TRAN; SELECT 1", "ROLLBACK", "SELECT * FROM OPENROWSET('SQLNCLI', 'x', 'SELECT 1')", "xp_cmdshell 'dir'",
                "USE master", "/* ' */ DELETE FROM t", "SELECT 1 /* -- */; DELETE FROM t",
                "SELECT 1DELETE FROM t", "SELECT 1COMMIT", "SELECT 1KILL 55", "SELECT 1DBCC FREEPROCCACHE", "SELECT 0x1DELETE FROM t", "SELECT 1e1DELETE FROM t", "SELECT 0xKILL 55", "SELECT 0xABKILL 55", "SELECT 1eCOMMIT", "SELECT 1eDELETE FROM t WHERE 1 = 1eCOMMIT"}) {
            check(refused(() -> readOnlyBatches(write, none)), "a write statement is refused: " + write);
        }
        for (String mode : new String[] {"rw", "deny", "RO", ""}) {
            check(refused(() -> main(new String[] {mode, "db", "/nonexistent"})), "mode " + mode + " is refused");
        }
        check(refused(() -> main(new String[] {"ro", "db"})), "missing script is refused");
        List<String> calls = new ArrayList<>();
        Connection fake = fakeConnection(calls);
        check(runRolledBack(fake, List.of("SELECT 1", "SELECT 2"), null) == 2 && calls.equals(List.of("setAutoCommit:false", "execute:SELECT 1", "execute:SELECT 2", "rollback")),
                "auto-commit off first, rollback last: " + calls);
        calls.clear();
        check(refusedAny(() -> runRolledBack(fake, List.of("SELECT 1", "FAIL", "SELECT 3"), null)) && calls.equals(List.of("setAutoCommit:false", "execute:SELECT 1", "execute:FAIL", "rollback")),
                "a failing batch still ends in a rollback, and stops the rest: " + calls);
        Path scratch = Files.createTempDirectory("onesql-scan");
        Files.createDirectories(scratch.resolve("sub/deeper"));
        Files.writeString(scratch.resolve("sub/deeper/w.sql"), "SELECT 1\nGO\nINSERT INTO t VALUES (1)", StandardCharsets.UTF_8);
        check(refused(() -> scanEvidence(scratch)), "a write statement in a subdirectory of the evidence folder is refused");
        Files.writeString(scratch.resolve("sub/deeper/w.sql"), "SELECT 1", StandardCharsets.UTF_8);
        check(scanEvidence(scratch) == 1, "a read in a subdirectory is scanned and accepted");
        if (evidence != null) {
            int scripts = scanEvidence(evidence);
            check(scripts > 0, "no evidence script found in " + evidence);
            System.out.println("evidence scripts accepted: " + scripts);
        }
        System.out.println("OneSql self-test: ok");
    }

    interface Call { void run() throws Exception; }

    static boolean refusedAny(Call call) {
        try {
            call.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** A Connection whose Statement records what runs; a batch "FAIL" throws as the server would. */
    static Connection fakeConnection(List<String> calls) {
        java.lang.reflect.InvocationHandler statement = (proxy, method, args) -> {
            switch (method.getName()) {
                case "execute":
                    calls.add("execute:" + args[0]);
                    if ("FAIL".equals(args[0])) throw new SQLException("failed", "42000", 1);
                    return false;
                case "getUpdateCount": return -1;
                case "getMoreResults": return false;
                default: return null;
            }
        };
        java.lang.reflect.InvocationHandler connection = (proxy, method, args) -> {
            switch (method.getName()) {
                case "createStatement":
                    return java.lang.reflect.Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, statement);
                case "setAutoCommit": calls.add("setAutoCommit:" + args[0]); return null;
                case "rollback": calls.add("rollback"); return null;
                default: return null;
            }
        };
        return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, connection);
    }

    /** Every .sql file under the folder, subdirectories included, must pass readOnlyBatches (evidence-*.sql create the evidence account: run by the DBA, never through OneSql). Returns the number of scripts. */
    static int scanEvidence(Path folder) throws Exception {
        int scripts = 0;
        try (var files = Files.walk(folder)) {
            for (Path f : (Iterable<Path>) files.sorted()::iterator) {
                String name = f.getFileName().toString();
                if (!Files.isRegularFile(f) || !name.endsWith(".sql") || name.startsWith("evidence-")) continue;
                readOnlyBatches(Files.readString(f, StandardCharsets.UTF_8), new LinkedHashMap<>());
                scripts++;
            }
        }
        return scripts;
    }

    static boolean refused(Call call) {
        try {
            call.run();
            return false;
        } catch (IllegalArgumentException | IllegalStateException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    static void run(Connection c, String sql, PublicKey pub) throws Exception {
        try (Statement st = c.createStatement()) {
            st.setQueryTimeout(300);
            boolean rs = st.execute(sql);
            while (true) {
                if (rs) {
                    try (ResultSet r = st.getResultSet()) {
                        print(r, pub);
                    }
                } else if (st.getUpdateCount() == -1) {
                    break;
                }
                rs = st.getMoreResults();
            }
        } catch (SQLException e) {
            System.out.println("SQLERROR " + e.getErrorCode() + " " + e.getMessage());
            throw e;
        }
    }

    static void print(ResultSet r, PublicKey pub) throws Exception {
        ResultSetMetaData md = r.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder h = new StringBuilder("  #");
        for (int i = 1; i <= cols; i++) h.append(i == 1 ? " " : " | ").append(md.getColumnLabel(i));
        System.out.println(h);
        int rows = 0;
        while (r.next()) {
            rows++;
            StringBuilder s = new StringBuilder("  ");
            for (int i = 1; i <= cols; i++) {
                String v = r.getString(i);
                if (md.getColumnLabel(i).equalsIgnoreCase("one_time_secret")) {
                    if (pub == null) throw new IllegalStateException("secret column without ENC_PUBKEY");
                    v = v == null ? "NULL" : "ENC:" + encrypt(v, pub);
                }
                s.append(i == 1 ? "" : " | ").append(v);
            }
            System.out.println(s);
        }
        System.out.println("  (" + rows + " rows)");
    }

    static List<String> parse(String script, Map<String, String> overrides) {
        Map<String, String> vars = new LinkedHashMap<>();
        List<String> batches = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : script.split("\\R", -1)) {
            String t = line.trim();
            if (t.startsWith(":setvar ")) {
                String[] p = t.split("\\s+", 3);
                vars.put(p[1], p[2]);
                continue;
            }
            if (t.startsWith(":")) continue;
            if (t.equalsIgnoreCase("GO")) {
                add(batches, cur);
                cur = new StringBuilder();
                continue;
            }
            cur.append(line).append('\n');
        }
        add(batches, cur);
        vars.putAll(overrides);
        List<String> out = new ArrayList<>();
        for (String b : batches) {
            for (Map.Entry<String, String> e : vars.entrySet()) b = b.replace("$(" + e.getKey() + ")", e.getValue());
            if (b.contains("$(")) throw new IllegalStateException("unresolved variable in batch: " + b.substring(b.indexOf("$("), Math.min(b.length(), b.indexOf("$(") + 30)));
            out.add(b);
        }
        return out;
    }

    static void add(List<String> batches, StringBuilder b) {
        if (!stripComments(b.toString()).isBlank()) batches.add(b.toString());
    }

    static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ").replaceAll("N?'(?:[^']|'')*'", "''");
    }

    static String firstComment(String b) {
        for (String l : b.split("\n")) {
            String t = l.trim();
            if (t.startsWith("-- =====") || t.startsWith("-- V") || t.startsWith("-- Q")) return t;
        }
        return "";
    }

    static OAEPParameterSpec oaep() {
        return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    }

    static PublicKey publicKey(String pemPath) throws Exception {
        String pem = Files.readString(Path.of(pemPath)).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    static String encrypt(String v, PublicKey k) throws Exception {
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(Cipher.ENCRYPT_MODE, k, oaep());
        return Base64.getEncoder().encodeToString(c.doFinal(v.getBytes(StandardCharsets.UTF_8)));
    }

    static String decrypt(String b64, String keyPath) throws Exception {
        String pem = Files.readString(Path.of(keyPath)).replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        PrivateKey k = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Cipher c = Cipher.getInstance("RSA/ECB/OAEPPadding");
        c.init(Cipher.DECRYPT_MODE, k, oaep());
        return new String(c.doFinal(Base64.getDecoder().decode(b64)), StandardCharsets.UTF_8);
    }
}
