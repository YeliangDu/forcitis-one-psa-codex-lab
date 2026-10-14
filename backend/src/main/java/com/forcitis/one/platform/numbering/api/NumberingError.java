package com.forcitis.one.platform.numbering.api;

import com.forcitis.one.kernel.ErrorCode;

public enum NumberingError implements ErrorCode {
    PLATFORM_NUMBERING_RULE_NOT_FOUND,
    PLATFORM_NUMBERING_RULE_INVALID,
    PLATFORM_NUMBERING_RULE_TRANSITION_ILLEGAL,
    PLATFORM_NUMBERING_CONCURRENT_UPDATE
}
