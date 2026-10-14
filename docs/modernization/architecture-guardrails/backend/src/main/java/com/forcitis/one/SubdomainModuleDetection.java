package com.forcitis.one;

import java.util.stream.Stream;
import org.springframework.modulith.ApplicationModule;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.JavaPackage;

/**
 * Package layout com.forcitis.one.&lt;subdomain&gt;.&lt;bc&gt; (GLOSSARY.md §1). Each bounded context is a top-level
 * application module (id "cost.timesheet"); the subdomain package is only a namespace. A direct sub-package that is
 * itself annotated with @ApplicationModule (the shared kernel) is a module on its own.
 */
public class SubdomainModuleDetection implements ApplicationModuleDetectionStrategy {

    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
        return basePackage.getDirectSubPackages().stream()
                .flatMap(subdomain -> subdomain.getAnnotation(ApplicationModule.class).isPresent()
                        ? Stream.of(subdomain)
                        : subdomain.getDirectSubPackages().stream());
    }
}
