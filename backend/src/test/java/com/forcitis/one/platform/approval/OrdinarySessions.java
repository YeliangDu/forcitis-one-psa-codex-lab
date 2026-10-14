package com.forcitis.one.platform.approval;

import com.forcitis.one.platform.identity.api.SessionRoles;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;

/** A caller as an ordinary, confirmed session is: the permissions of the roles and the mark of the session. */
public final class OrdinarySessions {

    private OrdinarySessions() {
    }

    public static UserRequestPostProcessor user(String name) {
        return SecurityMockMvcRequestPostProcessors.user(name).authorities(with());
    }

    public static List<GrantedAuthority> with(String... permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + SessionRoles.ORDINARY));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        return authorities;
    }
}
