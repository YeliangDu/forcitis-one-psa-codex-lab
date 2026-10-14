package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.CurrentUserView;
import java.util.List;

/** userId is a string because A8 ids are signed 64-bit numbers and JavaScript numbers are not. */
record CurrentUserResponse(String userId, String displayName, Long orgUnitId, String orgUnitName, List<String> permissionCodes,
                           List<NavigationResponse> navigation) {

    static CurrentUserResponse of(CurrentUserView view) {
        return new CurrentUserResponse(String.valueOf(view.userId()), view.displayName(), view.orgUnitId(), view.orgUnitName(),
                view.permissionCodes(), view.navigation().stream().map(NavigationResponse::of).toList());
    }
}
