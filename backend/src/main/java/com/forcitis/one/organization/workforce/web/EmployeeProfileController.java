package com.forcitis.one.organization.workforce.web;

import com.forcitis.one.organization.workforce.api.EmployeeProfiles;
import com.forcitis.one.organization.workforce.api.ProfileChangeRequestId;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current profile of an employee, changed in place by the personnel specialist; the caller is the signed-in user. A change that needs a second person answers 202 with the request. */
@RestController
@RequestMapping("/api/v1/organization/employees")
class EmployeeProfileController {
    private final EmployeeProfiles profiles;

    EmployeeProfileController(EmployeeProfiles profiles) {
        this.profiles = profiles;
    }

    @PostMapping("/{employeeId}:change-profile")
    @PreAuthorize("hasAuthority('" + WorkforcePermissions.PERSONNEL_COMMAND_CREATE + "')")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "changed at once"),
            @ApiResponse(responseCode = "202", description = "waits for a second person to confirm", content = @Content(schema = @Schema(implementation = AwaitingSecondPersonResponse.class)))})
    ResponseEntity<AwaitingSecondPersonResponse> change(@PathVariable UUID employeeId, @RequestBody ChangeEmployeeProfileRequest request) {
        Optional<ProfileChangeRequestId> waiting = profiles.change(request.toCommand(employeeId));
        return waiting.map(id -> ResponseEntity.accepted().body(new AwaitingSecondPersonResponse(id.value()))).orElseGet(() -> ResponseEntity.ok().build());
    }
}
