package com.forcitis.one.platform.identity.web;

record EmergencyLoginRequest(String loginName, String password, String totpCode) {

    @Override
    public String toString() {
        return "EmergencyLoginRequest[loginName=" + loginName + "]";
    }
}
