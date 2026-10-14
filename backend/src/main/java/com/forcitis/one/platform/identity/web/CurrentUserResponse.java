package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.CurrentUserView;
import java.util.List;

/** userId is a string because external ids are signed 64-bit numbers and JavaScript numbers are not; the same holds for orgUnitId. */
record CurrentUserResponse(String userId, String displayName, String orgUnitId, String orgUnitName, List<String> permissionCodes,
                           List<NavigationResponse> navigation) {

    static CurrentUserResponse of(CurrentUserView view) {
        return new CurrentUserResponse(String.valueOf(view.userId()), view.displayName(), view.orgUnitId() == null ? null : String.valueOf(view.orgUnitId()), view.orgUnitName(),
                view.permissionCodes(), view.navigation().stream().map(NavigationResponse::of).toList());
    }
}
