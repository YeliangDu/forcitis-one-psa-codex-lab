package com.forcitis.one.platform.identity.application;

/** Shown once to the people at the terminal and never stored in the clear; toString hides both secrets so they cannot reach a log line. */
public record IssuedEmergencyCredentials(String loginName, String password, String totpSecretBase32) {

    @Override
    public String toString() {
        return "IssuedEmergencyCredentials[loginName=" + loginName + ", password=<hidden>, totpSecretBase32=<hidden>]";
    }
}
