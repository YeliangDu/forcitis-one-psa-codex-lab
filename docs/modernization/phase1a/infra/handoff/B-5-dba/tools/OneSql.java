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
    static final Pattern WRITE = Pattern.compile("\\b(CREATE|ALTER|DROP|GRANT|DENY|REVOKE|INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE\\s+(?!AS\\b)|TRUNCATE|BACKUP|RESTORE|SHUTDOWN|KILL|DBCC)\\b",
            Pattern.CASE_INSENSITIVE);

    public static void main(String[] a) throws Exception {
        if (a.length == 1 && a[0].equals("--self-test")) {
            selfTest();
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
            System.out.println("DONE batches=" + n);
        }
    }

    /** The script's batches, refused as a whole when any batch carries a write keyword (comments ignored). */
    static List<String> readOnlyBatches(String script, Map<String, String> vars) {
        List<String> batches = parse(script, vars);
        for (String b : batches) {
            Matcher m = WRITE.matcher(stripComments(b));
            if (m.find()) throw new IllegalStateException("read-only script contains write keyword: " + m.group());
        }
        return batches;
    }

    /** No database: a write statement is refused, a read passes, any mode but ro is refused before the script is read. */
    static void selfTest() throws Exception {
        Map<String, String> none = new LinkedHashMap<>();
        check(readOnlyBatches("SELECT name FROM sys.tables\nGO\n-- DELETE in a comment is fine\nSELECT 1\n", none).size() == 2, "reads pass, comments are not keywords");
        for (String write : new String[] {"DELETE FROM t", "update t set a = 1", "INSERT INTO t VALUES (1)", "DROP TABLE t", "GRANT SELECT ON t TO u",
                "ALTER LOGIN x WITH PASSWORD = 'p'", "EXEC sp_who", "TRUNCATE TABLE t", "MERGE t USING s ON 1 = 1 WHEN MATCHED THEN DELETE;",
                "SELECT 1\nGO\nCREATE TABLE t (a int)"}) {
            check(refused(() -> readOnlyBatches(write, none)), "a write statement is refused: " + write);
        }
        for (String mode : new String[] {"rw", "deny", "RO", ""}) {
            check(refused(() -> main(new String[] {mode, "db", "/nonexistent"})), "mode " + mode + " is refused");
        }
        check(refused(() -> main(new String[] {"ro", "db"})), "missing script is refused");
        System.out.println("OneSql self-test: ok");
    }

    interface Call { void run() throws Exception; }

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
