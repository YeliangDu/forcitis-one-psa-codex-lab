package com.forcitis.one.platform.identity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Emergency accounts need at least 12 characters and complexity (user decision). */
public final class EmergencyPasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private EmergencyPasswordPolicy() {
    }

    public static List<String> violations(String password, String loginName) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < MIN_LENGTH) {
            problems.add("at least " + MIN_LENGTH + " characters");
            return problems;
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("at most " + MAX_LENGTH + " characters");
        }
        int classes = 0;
        classes += password.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        classes += password.chars().anyMatch(Character::isDigit) ? 1 : 0;
        classes += password.chars().anyMatch(c -> !Character.isLetterOrDigit(c)) ? 1 : 0;
        if (classes < 3) {
            problems.add("at least three of: lower case, upper case, digit, symbol");
        }
        if (loginName != null && loginName.length() >= 3
                && password.toLowerCase(Locale.ROOT).contains(loginName.toLowerCase(Locale.ROOT))) {
            problems.add("must not contain the login name");
        }
        if (password.chars().distinct().count() < 6) {
            problems.add("at least six different characters");
        }
        return problems;
    }
}
