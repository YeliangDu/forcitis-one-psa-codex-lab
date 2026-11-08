package com.forcitis.one.platform.identity.api;

/** Bridge to production discovery without an API dependency on internal layers (G3). */
public interface PermissionCatalogueProvider {
    PermissionCatalogue catalogue();
    void validate(String code);
}
