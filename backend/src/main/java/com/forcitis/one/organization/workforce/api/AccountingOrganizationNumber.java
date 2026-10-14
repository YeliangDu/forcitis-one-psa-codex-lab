package com.forcitis.one.organization.workforce.api;

import com.forcitis.one.kernel.ValueObject;

/** The numeric code of the legal entity in the accounting system; the letter code stays for document numbers. */
@ValueObject
public record AccountingOrganizationNumber(long value) {
    public AccountingOrganizationNumber {
        if (value <= 0) {
            throw new IllegalArgumentException("an accounting organization number is a positive number");
        }
    }
}
