package com.forcitis.one.organization.recruitment.api;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorCode;
import com.forcitis.one.kernel.ErrorKind;

public enum RecruitmentError implements ErrorCode {
    ORGANIZATION_RECRUITMENT_REQUISITION_NOT_FOUND(ErrorKind.NOT_FOUND),
    ORGANIZATION_RECRUITMENT_POSITION_NOT_ACTIVE(ErrorKind.UNPROCESSABLE),
    ORGANIZATION_RECRUITMENT_ORG_UNIT_INVALID(ErrorKind.UNPROCESSABLE),
    ORGANIZATION_RECRUITMENT_COUNT_INVALID(ErrorKind.UNPROCESSABLE),
    ORGANIZATION_RECRUITMENT_CLOSE_REASON_INVALID(ErrorKind.UNPROCESSABLE);

    private final ErrorKind kind;

    RecruitmentError(ErrorKind kind) {
        this.kind = kind;
    }

    public DomainException refusal(String message) {
        return new DomainException(this, kind, message);
    }
}
