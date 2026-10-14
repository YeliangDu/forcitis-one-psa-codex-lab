package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.ErrorCode;

enum WebError implements ErrorCode {
    PLATFORM_WEB_REQUEST_INVALID,
    PLATFORM_WEB_CORRELATION_INVALID,
    PLATFORM_WEB_SOURCE_UNAVAILABLE,
    PLATFORM_WEB_SOURCE_BROKEN,
    PLATFORM_WEB_UNEXPECTED
}
