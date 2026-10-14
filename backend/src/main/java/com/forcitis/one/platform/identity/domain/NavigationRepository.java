package com.forcitis.one.platform.identity.domain;

import java.util.List;

public interface NavigationRepository {

    /** Enabled items only. */
    List<NavigationItem> findEnabled();
}
