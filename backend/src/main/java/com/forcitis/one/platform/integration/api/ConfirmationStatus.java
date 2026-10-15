package com.forcitis.one.platform.integration.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SourceReferenceConfirmationStatus")
public enum ConfirmationStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}
