package com.forcitis.one.platform.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RULE-317 (user decision): employees have no password; emergency accounts need at least 12 characters and complexity. */
class EmergencyPasswordPolicyTest {

    @Test
    @DisplayName("RULE-317 at least 12 characters (the old 4-16 character form is gone)")
    void length() {
        assertThat(EmergencyPasswordPolicy.violations("Ab1!Ab1!Ab1", "admin")).isNotEmpty();
        assertThat(EmergencyPasswordPolicy.violations("Ab1!Cd2@Ef3#", "admin")).isEmpty();
        assertThat(EmergencyPasswordPolicy.violations(null, "admin")).isNotEmpty();
        assertThat(EmergencyPasswordPolicy.violations("", "admin")).isNotEmpty();
        assertThat(EmergencyPasswordPolicy.violations("Ab1!".repeat(40), "admin")).as("too long").isNotEmpty();
    }

    @Test
    @DisplayName("RULE-317 complexity: three of four character classes, several different characters, not the login name")
    void complexity() {
        assertThat(EmergencyPasswordPolicy.violations("alllowercaseletters", "admin")).as("one class").isNotEmpty();
        assertThat(EmergencyPasswordPolicy.violations("lowerANDUPPERcase", "admin")).as("two classes").isNotEmpty();
        assertThat(EmergencyPasswordPolicy.violations("lowerUPPER123456", "admin")).as("three classes").isEmpty();
        assertThat(EmergencyPasswordPolicy.violations("aaaaaaAAAAAA111111", "admin")).as("five distinct characters").isNotEmpty();
        assertThat(EmergencyPasswordPolicy.violations("xxBreakGlass123!!", "breakglass")).as("contains the login name").isNotEmpty();
        assertThat(EmergencyPasswordPolicy.violations("Str0ng!Passphrase#42", "breakglass")).isEmpty();
    }
}
