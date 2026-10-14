package com.forcitis.one.platform.identity.web;

import com.forcitis.one.platform.identity.application.NavigationEntry;
import java.util.List;

record NavigationResponse(String name, String routePath, List<NavigationResponse> children) {

    static NavigationResponse of(NavigationEntry entry) {
        return new NavigationResponse(entry.name(), entry.routePath(), entry.children().stream().map(NavigationResponse::of).toList());
    }
}
