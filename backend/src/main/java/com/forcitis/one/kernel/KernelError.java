package com.forcitis.one.kernel;

/** The refusals every module shares (CS-2.24): they are defined here and nowhere else, so one meaning has one code. */
public enum KernelError implements ErrorCode {
    KERNEL_TRANSITION_ILLEGAL(ErrorKind.CONFLICT),
    KERNEL_CONCURRENT_CHANGE(ErrorKind.CONFLICT);

    private final ErrorKind kind;

    KernelError(ErrorKind kind) {
        this.kind = kind;
    }

    public DomainException refusal(String message) {
        return new DomainException(this, kind, message);
    }
}
