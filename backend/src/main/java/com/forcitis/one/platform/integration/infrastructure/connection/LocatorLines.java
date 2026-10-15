package com.forcitis.one.platform.integration.infrastructure.connection;

import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.DatabaseLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.GrantLocator;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.Locator;
import com.forcitis.one.platform.integration.infrastructure.connection.EffectiveGrants.OverriddenGrant;
import com.forcitis.one.platform.integration.infrastructure.connection.ReadOnlyBaseline.Reason;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The locator lines of the read-only verification command (READONLY_VERIFY_ACCEPTANCE.md section 3a): two spaces, the item, the kind and the
 * fields in a fixed order. A catalog name is quoted with minimal escapes so that it can neither break the line nor forge another one; a
 * missing value is "-", and the login's own user is "SELF" because its name is the login name.
 */
final class LocatorLines {

    static final String ABSENT = "-";
    static final String SELF = "SELF";
    private static final String BARE = "[A-Z0-9_-]+";

    private LocatorLines() {
    }

    static String violation(char item, Reason reason, Locator locator) {
        String fields = switch (locator) {
            case DatabaseLocator database -> " database=" + quoted(database.database());
            case GrantLocator grant -> grant(grant);
        };
        return "  (" + item + ") violation reason=" + reason.name() + fields;
    }

    static String overridden(OverriddenGrant overridden) {
        return "  (g) overridden" + grant(overridden.grant()) + " by=" + overridden.by().name();
    }

    private static String grant(GrantLocator grant) {
        return " class=" + bare(grant.classDescription()) + " schema=" + quoted(grant.schema()) + " object=" + quoted(grant.object())
                + " objectId=" + grant.objectId() + " permission=" + quoted(grant.permission()) + " state=" + state(grant.state())
                + " grantee=" + (grant.self() ? SELF : quoted(grant.grantee())) + " granteeType=" + bare(grant.granteeType());
    }

    private static String state(String state) {
        return switch (state) {
            case "G" -> "GRANT";
            case "W" -> "GRANT_WITH_GRANT_OPTION";
            default -> ABSENT;
        };
    }

    private static String bare(@Nullable String value) {
        return value != null && value.trim().matches(BARE) ? value.trim() : ABSENT;
    }

    static String quoted(@Nullable String name) {
        if (name == null) {
            return ABSENT;
        }
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            switch (c) {
                case '\\' -> quoted.append("\\\\");
                case '"' -> quoted.append("\\\"");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (escaped(name, i)) {
                        quoted.append(String.format(Locale.ROOT, "\\u%04X", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    /** ISO control, line and paragraph separators, format characters (U+202E and the like) and a surrogate that is not part of a pair. */
    private static boolean escaped(String name, int index) {
        char c = name.charAt(index);
        if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
            int codePoint = name.codePointAt(Character.isLowSurrogate(c) && index > 0 && Character.isHighSurrogate(name.charAt(index - 1)) ? index - 1 : index);
            return !Character.isSupplementaryCodePoint(codePoint) || Character.getType(codePoint) == Character.FORMAT;
        }
        int type = Character.getType(c);
        return Character.isISOControl(c) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR || type == Character.FORMAT;
    }
}
