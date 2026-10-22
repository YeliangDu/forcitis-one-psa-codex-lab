package com.forcitis.one.platform.file.domain;

import java.util.Locale;
import java.util.Set;

/** The extension rule of ADR-0070 section 5 item 1, which counts together with the content rule: either makes a file contain macros. */
public final class MacroNames {
    private static final Set<String> EXTENSIONS = Set.of("docm", "dotm", "xlsm", "xltm", "xlsb", "xlam", "xla", "pptm", "potm", "ppsm", "ppam", "sldm");

    private MacroNames() {
    }

    public static boolean matches(String originalName) {
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(originalName.substring(dot + 1).strip().toLowerCase(Locale.ROOT));
    }
}
