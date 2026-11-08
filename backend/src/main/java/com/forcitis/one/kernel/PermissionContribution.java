package com.forcitis.one.kernel;

import java.util.List;

/** One module's explicit permission declarations; never derived from use sites. */
public interface PermissionContribution {
    String owner();
    List<PermissionDeclaration> permissions();
}
