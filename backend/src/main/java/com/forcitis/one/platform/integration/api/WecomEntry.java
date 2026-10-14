package com.forcitis.one.platform.integration.api;

/** Where a WeCom OAuth code came from (RULE-322/323; PC scan login). */
public enum WecomEntry {
    /** WeCom workbench web app (老系统 H5 内嵌页). */
    WORKBENCH,
    MINI_PROGRAM,
    QR_CODE_LOGIN
}
