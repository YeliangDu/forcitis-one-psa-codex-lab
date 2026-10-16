package com.forcitis.one;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ADR-0043 section 4, item 5: finds the places that put a direct identifier (address, browser, login name, WeCom user id, person name) into an audit text. */
final class AuditDetailPolicy {
    static final String RULE = "DIRECT_IDENTIFIER_IN_DETAIL";

    /** Calls whose arguments become, or lead to, an audit detail. */
    private static final Pattern CALL_HEAD = Pattern.compile("new\\s+AuditEntry\\s*\\(|\\.reject\\s*\\(|new\\s+(?:Outcome\\.)?Failed\\s*\\(");
    private static final Pattern DESCRIBE_HEAD = Pattern.compile("String\\s+describe\\s*\\([^)]*\\)\\s*\\{");
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("\"[^\"\\n]*(?:address|agent|loginName|operator|witness)="),
            Pattern.compile("\"sourceAddress\""),
            Pattern.compile("(?i)(?:login name|wecom user id)\\s*\"\\s*\\+"),
            Pattern.compile("\\.address\\(\\)"),
            Pattern.compile("\\buserAgent\\(\\)"),
            Pattern.compile("\\bgetRemoteAddr\\(\\)"),
            Pattern.compile("\\bgetHeader\\("),
            Pattern.compile("\\bloginName\\b"),
            Pattern.compile("\\bwecomUserId\\b"));

    private AuditDetailPolicy() {
    }

    /** The forbidden fragments found in the audit-bound parts of one source; empty when it is clean. */
    static List<String> breaches(String source) {
        String code = JavaCode.withoutComments(source);
        List<String> found = new ArrayList<>();
        Matcher call = CALL_HEAD.matcher(code);
        while (call.find()) {
            scan(code.substring(call.end(), closing(code, call.end() - 1, '(', ')')), found);
        }
        Matcher describe = DESCRIBE_HEAD.matcher(code);
        while (describe.find()) {
            scan(code.substring(describe.end(), closing(code, describe.end() - 1, '{', '}')), found);
        }
        return found;
    }

    private static void scan(String part, List<String> found) {
        for (Pattern forbidden : FORBIDDEN) {
            Matcher m = forbidden.matcher(part);
            if (m.find()) {
                found.add(m.group().strip());
            }
        }
    }

    private static int closing(String code, int open, char up, char down) {
        int depth = 0;
        boolean quoted = false;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\' && quoted) {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && c == up) {
                depth++;
            } else if (!quoted && c == down && --depth == 0) {
                return i;
            }
        }
        return code.length();
    }
}
