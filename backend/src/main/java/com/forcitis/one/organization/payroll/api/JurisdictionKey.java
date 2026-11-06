package com.forcitis.one.organization.payroll.api;

import com.forcitis.one.kernel.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/** The place a rule applies to: a national or locality code in upper case such as CN or CN_SH; repeats ck_payroll_rule_versions_jurisdiction_key. */
@ValueObject
public record JurisdictionKey(String value) {
    private static final Pattern SHAPE = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");

    public JurisdictionKey {
        Objects.requireNonNull(value, "JurisdictionKey");
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("JurisdictionKey must match " + SHAPE.pattern());
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
