package com.forcitis.one.organization.workforce.api;

/** Why a confirmation voided the request: the profile moved after the request was made, or the proposed WeCom user id now belongs to another employee. */
public enum ProfileVoidReason {
    BASE_CHANGED,
    WECOM_USER_ID_TAKEN
}
