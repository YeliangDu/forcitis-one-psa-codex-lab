package com.forcitis.one.platform.approval.web;

/** Body of a refused operation: the stable error code plus a human-readable message. */
record ApprovalErrorResponse(String errorCode, String message) {
}
