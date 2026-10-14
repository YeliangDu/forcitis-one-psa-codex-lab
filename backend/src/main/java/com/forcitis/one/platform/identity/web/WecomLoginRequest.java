package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.integration.api.WecomEntry;

/** code and state as received from the WeCom redirect. No user id: who logs in is decided by WeCom, never by the client. */
record WecomLoginRequest(String code, String state, WecomEntry entry) {
}
