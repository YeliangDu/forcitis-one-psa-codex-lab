package com.forcitis.one.platform.identity.domain;

import java.util.List;

public interface NavigationRepository {

    List<NavigationItem> findEnabled();
}
