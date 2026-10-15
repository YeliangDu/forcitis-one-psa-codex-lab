package com.forcitis.one;

public final class JavaCode {

    private JavaCode() {
    }

    /** Java text without comments (string literals and text blocks stay). */
    public static String withoutComments(String java) {
        StringBuilder out = new StringBuilder(java.length());
        int i = 0;
        int n = java.length();
        while (i < n) {
            char c = java.charAt(i);
            if (java.startsWith("\"\"\"", i)) {
                int end = java.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                out.append(java, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && java.charAt(j) != c && java.charAt(j) != '\n') {
                    j += java.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(n, j + 1);
                out.append(java, i, j);
                i = j;
            } else if (java.startsWith("//", i)) {
                while (i < n && java.charAt(i) != '\n') {
                    i++;
                }
            } else if (java.startsWith("/*", i)) {
                int end = java.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) {
                    if (java.charAt(k) == '\n') {
                        out.append('\n');
                    }
                }
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
