package com.forcitis.one;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** ADR-0043 section 4, item 5: finds the places that put a direct identifier (address, browser, login name, WeCom user id, person name) into an audit text. */
final class AuditDetailPolicy {
    static final String RULE = "DIRECT_IDENTIFIER_IN_DETAIL";

    /** Calls whose arguments become, or lead to, an audit detail. */
    private static final Pattern CALL_HEAD = Pattern.compile("new\\s+AuditEntry\\s*\\(|\\.reject\\s*\\(|new\\s+(?:Outcome\\.)?Failed\\s*\\(");
    private static final Pattern DESCRIBE_HEAD = Pattern.compile("String\\s+(?:describe|auditDetail)\\s*\\([^)]*\\)\\s*\\{");
    /** A text built first and handed over later: its initializer is audit-bound even where the write is in another file. */
    private static final Pattern DETAIL_VARIABLE = Pattern.compile("\\bString\\s+\\w*[dD]etail\\w*\\s*=\\s*([^;]*);");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[a-z]\\w*\\b");
    /** Types whose accessors hold a person's name or contact data: a variable of such a type is a person receiver. */
    private static final List<String> PERSON_TYPES = List.of("EmployeeProfile", "Employee", "SourcePerson", "MemberRow");
    private static final Pattern PERSON_ACCESSOR = Pattern.compile("\\.(?:displayName|fullName|realName|email|loginName|employeeCode|mobile)\\(\\)");
    private static final Pattern NAME_CALL = Pattern.compile("(\\w+)((?:\\.\\w+\\([^()]*\\))*)\\.name\\(\\)");
    private static final Pattern PERSON_HINT = Pattern.compile("(?i)\\w*(?:employee|profile|person|staff|member|holder|owner)\\w*");
    private static final Pattern NOT_A_PERSON = Pattern.compile("(?i)\\w*(?:id|status|kind|type|state|code)");
    private static final Pattern PERSON_CHAIN = Pattern.compile("(?i)\\.\\w*(?:employee|profile|person|staff|member|holder|owner)\\w*\\(");
    private static final List<Pattern> FORBIDDEN = List.of(
            Pattern.compile("\"[^\"\\n]*(?:address|agent|loginName|operator|witness)="),
            Pattern.compile("\"sourceAddress\""),
            Pattern.compile("(?i)(?:login name|wecom user id)\\s*\"\\s*\\+"),
            Pattern.compile("\\.address\\(\\)"),
            Pattern.compile("\\buserAgent\\(\\)"),
            Pattern.compile("\\bgetRemoteAddr\\(\\)"),
            Pattern.compile("\\bgetHeader\\("),
            Pattern.compile("\\bloginName\\b"),
            Pattern.compile("\\bwecomUserId\\b"),
            Pattern.compile("\\.getMessage\\(\\)"),
            Pattern.compile("\\.getLocalizedMessage\\(\\)"));
    /** Identifiers of a person's name or contact data, looked for outside string literals. */
    private static final List<Pattern> FORBIDDEN_IDENTIFIERS = List.of(
            Pattern.compile("\\bdisplayName\\b"),
            Pattern.compile("\\b(?:fullName|realName|employeeName|operatorName|witnessName)\\b"),
            Pattern.compile("\\bemail\\b"),
            Pattern.compile("\\bemployeeCode\\b"),
            Pattern.compile("\\bmobile\\b"));

    private AuditDetailPolicy() {
    }

    /** The forbidden fragments found in the audit-bound parts of one source; empty when it is clean. */
    static List<String> breaches(String source) {
        String code = JavaCode.withoutComments(source);
        Set<String> personVariables = personVariables(code);
        List<String> found = new ArrayList<>();
        Matcher call = CALL_HEAD.matcher(code);
        while (call.find()) {
            String arguments = code.substring(call.end(), closing(code, call.end() - 1, '(', ')'));
            scan(arguments, personVariables, found);
            Matcher name = IDENTIFIER.matcher(arguments);
            while (name.find()) {
                Matcher built = Pattern.compile("\\bString\\s+" + name.group() + "\\s*=\\s*([^;]*);").matcher(code);
                if (built.find()) {
                    scan(built.group(1), personVariables, found);
                }
            }
        }
        Matcher describe = DESCRIBE_HEAD.matcher(code);
        while (describe.find()) {
            scan(code.substring(describe.end(), closing(code, describe.end() - 1, '{', '}')), personVariables, found);
        }
        Matcher detail = DETAIL_VARIABLE.matcher(code);
        while (detail.find()) {
            scan(detail.group(1), personVariables, found);
        }
        return found;
    }

    /** The top-level arguments of the call whose opening bracket is at {@code open}. */
    static List<String> arguments(String code, int open) {
        List<String> arguments = new ArrayList<>();
        int end = closing(code, open, '(', ')');
        int depth = 0;
        int from = open + 1;
        boolean quoted = false;
        for (int i = open + 1; i < end; i++) {
            char c = code.charAt(i);
            if (c == '\\' && quoted) {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '(' || c == '[' || c == '{')) {
                depth++;
            } else if (!quoted && (c == ')' || c == ']' || c == '}')) {
                depth--;
            } else if (!quoted && depth == 0 && c == ',') {
                arguments.add(code.substring(from, i).strip());
                from = i + 1;
            }
        }
        arguments.add(code.substring(from, end).strip());
        return arguments;
    }

    private static Set<String> personVariables(String code) {
        Set<String> names = new HashSet<>();
        for (String type : PERSON_TYPES) {
            Matcher m = Pattern.compile("\\b" + type + "\\b\\s*>*\\s+(\\w+)\\s*(?=[=;,):])").matcher(code);
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    private static void scan(String part, Set<String> personVariables, List<String> found) {
        for (Pattern forbidden : FORBIDDEN) {
            Matcher m = forbidden.matcher(part);
            if (m.find()) {
                found.add(m.group().strip());
            }
        }
        String outsideLiterals = part.replaceAll("\"(?:[^\"\\\\\\n]|\\\\.)*\"", "\"\"");
        for (Pattern forbidden : FORBIDDEN_IDENTIFIERS) {
            Matcher m = forbidden.matcher(outsideLiterals);
            if (m.find()) {
                found.add(m.group().strip());
            }
        }
        Matcher accessor = PERSON_ACCESSOR.matcher(outsideLiterals);
        if (accessor.find()) {
            found.add(accessor.group().strip());
        }
        Matcher name = NAME_CALL.matcher(outsideLiterals);
        while (name.find()) {
            String receiver = name.group(1);
            boolean hinted = PERSON_HINT.matcher(receiver).matches() && !NOT_A_PERSON.matcher(receiver).matches();
            if (personVariables.contains(receiver) || hinted || PERSON_CHAIN.matcher(name.group(2)).find()) {
                found.add(name.group().strip());
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
