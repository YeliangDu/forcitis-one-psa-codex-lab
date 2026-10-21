package com.forcitis.one.platform.file.infrastructure.content;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Whether an encrypted PDF opens with the empty user password, by the standard security handler (ISO 32000-2 section 7.6.4, revisions 2
 * to 6). Such a file only restricts permissions and is scanned like any other (ADR-0070 section 6 item 1); one that needs an open password,
 * or whose encryption cannot be read, is refused.
 */
final class PdfOpenPassword {
    private static final byte[] PAD = HexFormat.of().parseHex("28bf4e5e4e758a4164004e56fffa01082e2e00b6d0683e802f0ca9fe6453697a");
    private static final Pattern ENCRYPT = Pattern.compile("/" + escapable("Encrypt") + "\\s*(?:(\\d+)\\s+(\\d+)\\s+R|(<<))");
    private static final Pattern ID = Pattern.compile("/" + escapable("ID") + "\\s*\\[\\s*");

    private PdfOpenPassword() {
    }

    /**
     * Empty when the file declares no encryption; true only when the empty user password opens every encryption dictionary the file
     * contains, under every /ID it carries. A decoy dictionary, an unreferenced object or a later trailer cannot make an encrypted file
     * pass: any candidate that does not open, or cannot be read, makes the answer false.
     */
    static Optional<Boolean> opensWithoutPassword(byte[] pdf) {
        String text = new String(pdf, StandardCharsets.ISO_8859_1);
        List<Integer> dictionaries = new ArrayList<>();
        Matcher encrypt = ENCRYPT.matcher(text);
        try {
            while (encrypt.find()) {
                if (encrypt.group(3) != null) {
                    dictionaries.add(encrypt.start(3));
                    continue;
                }
                Matcher object = Pattern.compile("(?<![0-9])" + encrypt.group(1) + "\\s+" + encrypt.group(2) + "\\s+obj\\s*").matcher(text);
                boolean found = false;
                while (object.find()) {
                    dictionaries.add(object.end());
                    found = true;
                }
                if (!found) {
                    return Optional.of(false);
                }
            }
            if (dictionaries.isEmpty()) {
                return Optional.empty();
            }
            List<byte[]> ids = ids(pdf, text);
            for (int start : dictionaries) {
                Map<String, Object> dictionary = new Reader(pdf, start).dictionary();
                if (!"Standard".equals(dictionary.get("Filter"))) {
                    return Optional.of(false);
                }
                for (byte[] id : ids) {
                    if (!emptyUserPasswordOpens(dictionary, id)) {
                        return Optional.of(false);
                    }
                }
            }
            return Optional.of(true);
        } catch (RuntimeException | GeneralSecurityException e) {
            return Optional.of(false);
        }
    }

    /** A name may spell any character as #hh (ISO 32000-2 section 7.3.5). */
    private static String escapable(String name) {
        StringBuilder pattern = new StringBuilder();
        for (char c : name.toCharArray()) {
            pattern.append("(?:").append(c).append("|#").append(String.format("%02x", (int) c)).append(")");
        }
        return "(?i:" + pattern + ")";
    }

    private static boolean emptyUserPasswordOpens(Map<String, Object> d, byte[] id) throws GeneralSecurityException {
        int revision = number(d, "R");
        if (revision < 2 || revision > 6) {
            return false;
        }
        byte[] owner = bytes(d, "O");
        byte[] user = bytes(d, "U");
        if (revision >= 5) {
            byte[] salt = Arrays.copyOfRange(user, 32, 40);
            byte[] hash = revision == 5 ? sha("SHA-256", salt) : hardenedHash(salt);
            return MessageDigest.isEqual(Arrays.copyOf(hash, 32), Arrays.copyOf(user, 32));
        }
        int keyBytes = revision == 2 ? 5 : d.containsKey("Length") ? number(d, "Length") / 8 : revision == 4 ? 16 : 5;
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(PAD);
        md5.update(owner, 0, 32);
        int permissions = (int) Long.parseLong(String.valueOf(d.get("P")));
        md5.update(new byte[] {(byte) permissions, (byte) (permissions >>> 8), (byte) (permissions >>> 16), (byte) (permissions >>> 24)});
        md5.update(id);
        if (revision >= 4 && Boolean.FALSE.equals(d.get("EncryptMetadata"))) {
            md5.update(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff});
        }
        byte[] key = Arrays.copyOf(md5.digest(), keyBytes);
        if (revision >= 3) {
            for (int i = 0; i < 50; i++) {
                key = Arrays.copyOf(MessageDigest.getInstance("MD5").digest(key), keyBytes);
            }
        }
        if (revision == 2) {
            return MessageDigest.isEqual(rc4(key, PAD), Arrays.copyOf(user, 32));
        }
        MessageDigest check = MessageDigest.getInstance("MD5");
        check.update(PAD);
        check.update(id);
        byte[] value = rc4(key, check.digest());
        for (int i = 1; i <= 19; i++) {
            byte[] round = key.clone();
            for (int j = 0; j < round.length; j++) {
                round[j] ^= (byte) i;
            }
            value = rc4(round, value);
        }
        return MessageDigest.isEqual(value, Arrays.copyOf(user, 16));
    }

    /** Algorithm 2.B for revision 6, with the empty password and no user key. */
    private static byte[] hardenedHash(byte[] salt) throws GeneralSecurityException {
        byte[] k = sha("SHA-256", salt);
        byte[] e = new byte[0];
        for (int round = 0; round < 64 || (e[e.length - 1] & 0xff) > round - 32; round++) {
            byte[] k1 = new byte[k.length * 64];
            for (int i = 0; i < 64; i++) {
                System.arraycopy(k, 0, k1, i * k.length, k.length);
            }
            Cipher aes = Cipher.getInstance("AES/CBC/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, 0, 16, "AES"), new IvParameterSpec(k, 16, 16));
            e = aes.doFinal(k1);
            int sum = 0;
            for (int i = 0; i < 16; i++) {
                sum += e[i] & 0xff;
            }
            k = sha(switch (sum % 3) {
                case 0 -> "SHA-256";
                case 1 -> "SHA-384";
                default -> "SHA-512";
            }, e);
        }
        return Arrays.copyOf(k, 32);
    }

    private static byte[] rc4(byte[] key, byte[] data) throws GeneralSecurityException {
        Cipher rc4 = Cipher.getInstance("ARCFOUR");
        rc4.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ARCFOUR"));
        return rc4.doFinal(data);
    }

    private static byte[] sha(String algorithm, byte[] data) throws GeneralSecurityException {
        return MessageDigest.getInstance(algorithm).digest(data);
    }

    private static List<byte[]> ids(byte[] pdf, String text) {
        List<byte[]> ids = new ArrayList<>();
        Matcher id = ID.matcher(text);
        while (id.find()) {
            ids.add((byte[]) new Reader(pdf, id.end()).value());
        }
        if (ids.isEmpty()) {
            ids.add(new byte[0]);
        }
        return ids;
    }

    private static int number(Map<String, Object> d, String key) {
        return Integer.parseInt(String.valueOf(d.get(key)));
    }

    private static byte[] bytes(Map<String, Object> d, String key) {
        Object value = d.get(key);
        if (!(value instanceof byte[] b) || b.length < 32) {
            throw new IllegalStateException(key + " is missing");
        }
        return b;
    }

    /** Just enough of the PDF syntax for an encryption dictionary: names, numbers, booleans, strings, arrays, dictionaries, references. */
    private static final class Reader {
        private final byte[] pdf;
        private int at;

        Reader(byte[] pdf, int at) {
            this.pdf = pdf;
            this.at = at;
        }

        Map<String, Object> dictionary() {
            space();
            expect("<<");
            Map<String, Object> values = new HashMap<>();
            while (true) {
                space();
                if (peek(">>")) {
                    at += 2;
                    return values;
                }
                if (pdf[at] != '/') {
                    throw new IllegalStateException("a key is expected");
                }
                String key = name();
                if (values.put(key, value()) != null) {
                    throw new IllegalStateException("a key appears twice: readers may disagree on which counts");
                }
            }
        }

        Object value() {
            space();
            byte c = pdf[at];
            if (peek("<<")) {
                return dictionary();
            }
            if (c == '<') {
                return hex();
            }
            if (c == '(') {
                return literal();
            }
            if (c == '/') {
                return name();
            }
            if (c == '[') {
                at++;
                Object first = null;
                while (true) {
                    space();
                    if (pdf[at] == ']') {
                        at++;
                        return first;
                    }
                    Object item = value();
                    first = first == null ? item : first;
                }
            }
            String token = token();
            if (token.equals("true") || token.equals("false")) {
                return Boolean.valueOf(token);
            }
            int mark = at;
            space();
            if (token.matches("\\d+") && at < pdf.length && Character.isDigit(pdf[at])) {
                String second = token();
                space();
                if (at < pdf.length && pdf[at] == 'R') {
                    at++;
                    return token + " " + second + " R";
                }
            }
            at = mark;
            return token;
        }

        private String name() {
            at++;
            int start = at;
            while (at < pdf.length && !delimiter(pdf[at]) && !Character.isWhitespace(pdf[at])) {
                at++;
            }
            String raw = new String(pdf, start, at - start, StandardCharsets.ISO_8859_1);
            Matcher escape = Pattern.compile("#([0-9A-Fa-f]{2})").matcher(raw);
            StringBuilder decoded = new StringBuilder();
            while (escape.find()) {
                escape.appendReplacement(decoded, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(escape.group(1), 16))));
            }
            escape.appendTail(decoded);
            return decoded.toString();
        }

        private String token() {
            int start = at;
            while (at < pdf.length && !delimiter(pdf[at]) && !Character.isWhitespace(pdf[at])) {
                at++;
            }
            if (start == at) {
                throw new IllegalStateException("a value is expected");
            }
            return new String(pdf, start, at - start, StandardCharsets.ISO_8859_1);
        }

        private byte[] hex() {
            at++;
            StringBuilder digits = new StringBuilder();
            while (pdf[at] != '>') {
                if (!Character.isWhitespace(pdf[at])) {
                    digits.append((char) pdf[at]);
                }
                at++;
            }
            at++;
            if (digits.length() % 2 == 1) {
                digits.append('0');
            }
            return HexFormat.of().parseHex(digits);
        }

        private byte[] literal() {
            at++;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int depth = 1;
            while (true) {
                int c = pdf[at++] & 0xff;
                if (c == '\\') {
                    int e = pdf[at++] & 0xff;
                    switch (e) {
                        case 'n' -> out.write('\n');
                        case 'r' -> out.write('\r');
                        case 't' -> out.write('\t');
                        case 'b' -> out.write('\b');
                        case 'f' -> out.write('\f');
                        case '\r' -> {
                            if (pdf[at] == '\n') {
                                at++;
                            }
                        }
                        case '\n' -> {
                        }
                        default -> {
                            if (e >= '0' && e <= '7') {
                                int octal = e - '0';
                                for (int i = 0; i < 2 && pdf[at] >= '0' && pdf[at] <= '7'; i++) {
                                    octal = octal * 8 + pdf[at++] - '0';
                                }
                                out.write(octal);
                            } else {
                                out.write(e);
                            }
                        }
                    }
                } else if (c == '(') {
                    depth++;
                    out.write(c);
                } else if (c == ')') {
                    if (--depth == 0) {
                        return out.toByteArray();
                    }
                    out.write(c);
                } else {
                    out.write(c);
                }
            }
        }

        private void space() {
            while (at < pdf.length && (Character.isWhitespace(pdf[at]) || pdf[at] == 0)) {
                at++;
            }
        }

        private boolean peek(String text) {
            return at + 1 < pdf.length && pdf[at] == text.charAt(0) && pdf[at + 1] == text.charAt(1);
        }

        private void expect(String text) {
            if (!peek(text)) {
                throw new IllegalStateException(text + " is expected");
            }
            at += 2;
        }

        private static boolean delimiter(byte c) {
            return "()<>[]{}/%".indexOf(c) >= 0;
        }
    }
}
