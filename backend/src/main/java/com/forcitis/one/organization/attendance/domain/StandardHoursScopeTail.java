package com.forcitis.one.organization.attendance.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** What a scope already holds: the highest version number ever used (voided ones count) and the start of its latest version in effect. */
public record StandardHoursScopeTail(int lastVersionNumber, @Nullable LocalDate latestInEffectFrom) {

    public static StandardHoursScopeTail empty() {
        return new StandardHoursScopeTail(0, null);
    }
}
