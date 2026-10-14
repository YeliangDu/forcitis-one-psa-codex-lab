package com.forcitis.one.platform.identity.application;

public record EmergencyLoginCommand(String loginName, String password, String totpCode) {

    /** The password and the code must never reach a log line. */
    @Override
    public String toString() {
        return "EmergencyLoginCommand[loginName=" + loginName + "]";
    }
}
