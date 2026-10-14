package com.forcitis.one;

import java.util.stream.Stream;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

/** A direct sub-package annotated with @ApplicationModule (the shared kernel) is a module on its own. */
public class SubdomainModuleDetection implements ApplicationModuleDetectionStrategy {
    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
        return basePackage.getDirectSubPackages().stream()
                .flatMap(subdomain -> subdomain.getAnnotation(ApplicationModule.class).isPresent()
                        ? Stream.of(subdomain)
                        : subdomain.getDirectSubPackages().stream());
    }
}
