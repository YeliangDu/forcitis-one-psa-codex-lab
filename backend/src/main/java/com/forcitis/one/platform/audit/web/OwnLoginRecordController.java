package com.forcitis.one.platform.audit.web;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.platform.audit.application.ListOwnLoginRecords;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The person is the session user and nothing in the request can change that. */
@RestController
@RequestMapping("/api/v1/platform/own-login-records")
class OwnLoginRecordController {

    private final ListOwnLoginRecords list;

    OwnLoginRecordController(ListOwnLoginRecords list) {
        this.list = list;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('" + AuditPermissions.OWN_LOGIN_RECORD_READ + "')")
    List<OwnLoginRecordResponse> list(@RequestParam(defaultValue = "50") int limit, UserId caller) {
        return list.execute(caller, limit).stream().map(OwnLoginRecordResponse::of).toList();
    }
}
