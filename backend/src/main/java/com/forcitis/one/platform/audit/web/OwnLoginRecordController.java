package com.forcitis.one.platform.audit.web;

import com.forcitis.one.kernel.CursorPage;
import com.forcitis.one.kernel.PageRequest;
import com.forcitis.one.platform.audit.application.ListOwnLoginRecords;
import com.forcitis.one.platform.audit.application.OwnLoginRecordPageView;
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
    CursorPage<OwnLoginRecordResponse> list(@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        OwnLoginRecordPageView page = list.execute(PageRequest.of(limit, cursor, "own-login-records"));
        return CursorPage.of(page.items().stream().map(OwnLoginRecordResponse::of).toList(), page.nextCursor());
    }
}
