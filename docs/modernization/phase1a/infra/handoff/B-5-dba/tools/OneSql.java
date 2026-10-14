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
 * usage: java -cp mssql-jdbc.jar OneSql.java <ro|rw> <database> <script> [VAR=VALUE ...]
 *   stdin: Nacos datasource yaml (DBA login), unless LOGIN_USER + LOGIN_SECRET_ENC + LOGIN_KEY are set
 *   ENC_PUBKEY: PEM public key; any result column named one_time_secret is printed only as RSA-OAEP(SHA-256) ciphertext
 *   ro mode: script must not contain DDL/DML keywords; runs inside a transaction that is rolled back.
 */
public class OneSql {
    static final String HOST = "rm-bp1690ep53t9778dp.sqlserver.rds.aliyuncs.com";
    static final Pattern WRITE = Pattern.compile("\\b(CREATE|ALTER|DROP|GRANT|DENY|REVOKE|INSERT|UPDATE|DELETE|MERGE|EXEC|EXECUTE\\s+(?!AS\\b)|TRUNCATE|BACKUP|RESTORE|SHUTDOWN|KILL|DBCC)\\b",
            Pattern.CASE_INSENSITIVE);

    public static void main(String[] a) throws Exception {
        String mode = a[0], db = a[1];
        String script = Files.readString(Path.of(a[2]), StandardCharsets.UTF_8);
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 3; i < a.length; i++) {
            int eq = a[i].indexOf('=');
            vars.put(a[i].substring(0, eq), a[i].substring(eq + 1));
        }
        List<String> batches = parse(script, vars);
        if (mode.equals("ro")) {
            for (String b : batches) {
                Matcher m = WRITE.matcher(stripComments(b));
                if (m.find()) throw new IllegalStateException("ro script contains write keyword: " + m.group());
            }
        }
        String user, secret;
        if (System.getenv("LOGIN_USER") != null) {
            user = System.getenv("LOGIN_USER");
            secret = decrypt(Files.readString(Path.of(System.getenv("LOGIN_SECRET_ENC"))).trim(), System.getenv("LOGIN_KEY"));
        } else {
            String yaml = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            int master = yaml.indexOf("master:");
            if (master < 0) throw new IllegalStateException("no master datasource in config");
            String tail = yaml.substring(master);
            user = field(tail, "username");
            secret = field(tail, "password");
            if (secret.startsWith("ENC(")) throw new IllegalStateException("encrypted password in config; stop");
        }
        String url = "jdbc:sqlserver://" + HOST + ":1433;databaseName=" + db
                + ";encrypt=true;trustServerCertificate=true;applicationName=forcitis-one-ops;loginTimeout=15";
        PublicKey pub = System.getenv("ENC_PUBKEY") == null ? null : publicKey(System.getenv("ENC_PUBKEY"));
        try (Connection c = DriverManager.getConnection(url, user, secret)) {
            secret = null;
            System.out.println("connected as " + user + " db=" + db + " mode=" + mode);
            boolean deny = mode.equals("deny");
            if (mode.equals("ro") || deny) c.setAutoCommit(false);
            int n = 0, allowed = 0;
            try {
                for (String b : batches) {
                    n++;
                    String head = firstComment(b);
                    System.out.println("---- batch " + n + (head.isEmpty() ? "" : " " + head));
                    if (!deny) {
                        run(c, b, pub);
                        continue;
                    }
                    try {
                        run(c, b, pub);
                        allowed++;
                        System.out.println("  UNEXPECTED: statement was allowed");
                    } catch (SQLException e) {
                        System.out.println("  DENIED as expected (error " + e.getErrorCode() + ")");
                    }
                }
            } finally {
                if (mode.equals("ro") || deny) c.rollback();
            }
            System.out.println("DONE batches=" + n + (deny ? " allowed=" + allowed : ""));
            if (allowed > 0) System.exit(3);
        }
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

    static String field(String yaml, String key) {
        Matcher m = Pattern.compile("(?m)^\\s*" + key + ":\\s*(.*?)\\s*$").matcher(yaml);
        if (!m.find()) throw new IllegalStateException("missing " + key);
        String v = m.group(1);
        if ((v.startsWith("'") && v.endsWith("'")) || (v.startsWith("\"") && v.endsWith("\""))) v = v.substring(1, v.length() - 1);
        return v;
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
