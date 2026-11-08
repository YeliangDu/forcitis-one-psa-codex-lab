package com.forcitis.one.platform.identity.infrastructure.permissions;

import com.forcitis.one.kernel.PermissionContribution;
import com.forcitis.one.platform.identity.api.PermissionCatalogue;
import com.forcitis.one.platform.identity.domain.SystemRoleDeclaration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Validate actual Spring discovery before role reconciliation can write grants. */
@Configuration
class PermissionCatalogueConfiguration {
    @Bean
    PermissionCatalogue permissionCatalogue(List<PermissionContribution> contributions, ObjectProvider<SystemRoleDeclaration> roles) {
        PermissionCatalogueSource source = new PermissionCatalogueSource();
        source.requireSameContributions(contributions);
        return source.catalogue(contributions, roles.getIfAvailable(() -> SystemRoleDeclaration.CURRENT));
    }
}
