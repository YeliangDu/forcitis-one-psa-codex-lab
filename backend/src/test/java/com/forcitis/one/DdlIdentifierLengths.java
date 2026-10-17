package com.forcitis.one;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DdlIdentifierLengths {
    private static final Pattern DOLLAR = Pattern.compile("\\$(?:[A-Za-z_][A-Za-z0-9_]*)?\\$");

    private DdlIdentifierLengths() {}

    static int bytes(String identifier) {
        return identifier.getBytes(StandardCharsets.UTF_8).length;
    }

    static Map<String, Integer> modelProblems(Map<String, DdlNamingTest.Schema> schemas) {
        Map<String, Integer> found = new TreeMap<>();
        schemas.forEach((name, schema) -> {
            add(found, "schema " + name, name);
            schema.tables.forEach((table, model) -> {
                String qualified = name + "." + table;
                add(found, "table " + qualified, table);
                model.columns.keySet().forEach(column -> add(found, "column " + qualified + "." + column, column));
                model.constraints.keySet().forEach(constraint -> add(found, "constraint " + qualified + "." + constraint, constraint));
            });
            schema.indexes.keySet().forEach(index -> add(found, "index " + name + "." + index, index));
        });
        return found;
    }

    static Map<String, Integer> sourceProblems(Map<String, String> migrations) {
        Map<String, Integer> found = new TreeMap<>();
        migrations.forEach((file, sql) -> identifiers(file, sql).forEach(name -> add(found, file + " identifier " + name, name)));
        return found;
    }

    private static void add(Map<String, Integer> found, String key, String name) {
        int size = bytes(name);
        if (size > 63) {
            found.put(key, size);
        }
    }

    // 原文检查覆盖已删除/改名的中间标识符；字符串和函数体留给专门的 SQL 检查。
    static List<String> identifiers(String sql) {
        return identifiers("<SQL>", sql);
    }

    private static List<String> identifiers(String file, String sql) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < sql.length();) {
            if (sql.startsWith("--", i)) {
                int end = sql.indexOf('\n', i);
                i = end < 0 ? sql.length() : end + 1;
            } else if (sql.startsWith("/*", i)) {
                i = blockEnd(file, sql, i);
            } else if (sql.charAt(i) == '\'') {
                boolean escaped = i > 0 && (sql.charAt(i - 1) == 'E' || sql.charAt(i - 1) == 'e')
                        && (i == 1 || !identifierPart(sql.codePointBefore(i - 1)));
                i = quotedEnd(file, sql, i, '\'', escaped);
            } else if (sql.charAt(i) == '"') {
                int end = quotedEnd(file, sql, i, '"', false);
                names.add(sql.substring(i + 1, end - 1).replace("\"\"", "\""));
                i = end;
            } else if (sql.charAt(i) == '$') {
                Matcher tag = DOLLAR.matcher(sql).region(i, sql.length());
                if (tag.lookingAt()) {
                    int end = sql.indexOf(tag.group(), tag.end());
                    if (end < 0) {
                        throw parseError(file, sql, i, "DDL 美元引用未闭合");
                    }
                    i = end + tag.group().length();
                } else {
                    i++;
                }
            } else if (Character.isLetter(sql.codePointAt(i)) || sql.charAt(i) == '_') {
                int start = i;
                i += Character.charCount(sql.codePointAt(i));
                while (i < sql.length() && identifierPart(sql.codePointAt(i))) {
                    i += Character.charCount(sql.codePointAt(i));
                }
                names.add(sql.substring(start, i));
            } else {
                i += Character.charCount(sql.codePointAt(i));
            }
        }
        return names;
    }

    private static boolean identifierPart(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_' || codePoint == '$';
    }

    private static IllegalArgumentException parseError(String file, String sql, int start, String message) {
        long line = 1 + sql.substring(0, start).chars().filter(c -> c == '\n').count();
        return new IllegalArgumentException(file + ":" + line + ": " + message);
    }

    private static int blockEnd(String file, String sql, int start) {
        int depth = 1;
        for (int i = start + 2; i < sql.length();) {
            if (sql.startsWith("/*", i)) {
                depth++;
                i += 2;
            } else if (sql.startsWith("*/", i)) {
                i += 2;
                if (--depth == 0) {
                    return i;
                }
            } else {
                i++;
            }
        }
        throw parseError(file, sql, start, "DDL 块注释未闭合");
    }

    private static int quotedEnd(String file, String sql, int start, char quote, boolean escaped) {
        for (int i = start + 1; i < sql.length(); i++) {
            if (escaped && sql.charAt(i) == '\\') {
                i++;
            } else if (sql.charAt(i) == quote) {
                if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) {
                    i++;
                } else {
                    return i + 1;
                }
            }
        }
        throw parseError(file, sql, start, "DDL 引用未闭合");
    }
}
