package com.forcitis.one.platform.identity.application;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.organization.workforce.api.EmployeeProfile;
import com.forcitis.one.organization.workforce.api.OrgDirectory;
import com.forcitis.one.organization.workforce.api.OrgUnitProfile;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.identity.domain.AccessProfile;
import com.forcitis.one.platform.identity.domain.AdministratorMode;
import com.forcitis.one.platform.identity.domain.AccountRepository;
import com.forcitis.one.platform.identity.domain.NavigationNode;
import com.forcitis.one.platform.identity.domain.NavigationRepository;
import com.forcitis.one.platform.identity.domain.NavigationTree;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetCurrentUserProfile {

    private final CurrentUser currentUser;
    private final OrgDirectory directory;
    private final AccountRepository accounts;
    private final NavigationRepository navigation;
    private final SessionContext session;
    private final SessionRegistry sessions;

    GetCurrentUserProfile(CurrentUser currentUser, OrgDirectory directory, AccountRepository accounts,
            NavigationRepository navigation, SessionContext session, SessionRegistry sessions) {
        this.session = session;
        this.sessions = sessions;
        this.currentUser = currentUser;
        this.directory = directory;
        this.accounts = accounts;
        this.navigation = navigation;
    }

    @Transactional(readOnly = true)
    public CurrentUserView execute() {
        UserId userId = currentUser.userId();
        Optional<EmployeeProfile> employee = directory.findEmployee(currentUser.employeeId());
        Optional<OrgUnitProfile> unit = employee.flatMap(e -> directory.findOrgUnit(e.orgUnitId()));
        boolean confirmed = sessions.identityConfirmed();
        AccessProfile profile = AccessProfile.of(confirmed ? accounts.findEnabledRoles(userId) : List.of());
        SessionMode mode = session.mode();
        Set<String> permissions = mode == SessionMode.ADMINISTRATOR ? profile.administratorModePermissionCodes()
                : profile.permissionCodes().stream().filter(code -> !AdministratorMode.PLATFORM_ONLY.contains(code)).collect(Collectors.toUnmodifiableSet());
        List<NavigationEntry> menu = confirmed ? NavigationTree.visibleTo(permissions, navigation.findEnabled())
                .stream().map(GetCurrentUserProfile::entry).toList() : List.of();
        return new CurrentUserView(userId, employee.map(EmployeeProfile::name).orElse(""),
                employee.map(e -> e.orgUnitId().value()).orElse(null), unit.map(OrgUnitProfile::name).orElse(""),
                permissions.stream().sorted().toList(), menu, mode, session.administratorModeExpiresAt().orElse(null), confirmed);
    }

    private static NavigationEntry entry(NavigationNode node) {
        return new NavigationEntry(node.name(), node.routePath(), node.children().stream().map(GetCurrentUserProfile::entry).toList());
    }
}
