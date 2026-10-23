package com.forcitis.one.platform.signing.api;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.ErrorKind;

public enum SigningError implements ErrorCode {
    PLATFORM_SIGNING_LEDGER_ENTRY_NOT_FOUND(ErrorKind.NOT_FOUND),
    PLATFORM_SIGNING_NO_VERSION_AT(ErrorKind.UNPROCESSABLE),
    PLATFORM_SIGNING_SEAL_TYPE_UNKNOWN(ErrorKind.UNPROCESSABLE),
    PLATFORM_SIGNING_ACTIVE_ENTRY_EXISTS(ErrorKind.CONFLICT),
    PLATFORM_SIGNING_VERSION_NOT_EDITABLE(ErrorKind.CONFLICT),
    PLATFORM_SIGNING_CONFIRMER_IS_AUTHOR(ErrorKind.UNPROCESSABLE);

    private final ErrorKind kind;

    SigningError(ErrorKind kind) {
        this.kind = kind;
    }

    public DomainException refusal(String message) {
        return new DomainException(this, kind, message);
    }
}
