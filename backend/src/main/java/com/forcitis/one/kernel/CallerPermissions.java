package com.forcitis.one.kernel;

/** Asks whether the signed-in caller holds a permission; for the rare use case that must decide on a permission itself. */
public interface CallerPermissions {

    boolean holds(String permissionCode);
}
