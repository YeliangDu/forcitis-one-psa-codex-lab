package com.forcitis.one.platform.web;

import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Every GET handler under /api is checked when the application starts, so a parameter UnknownQueryParameters cannot account for stops the start instead of misjudging requests. */
@Component
class QueryParameterContract implements SmartInitializingSingleton {
    private final ApplicationContext context;

    QueryParameterContract(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void afterSingletonsInstantiated() {
        check(context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class).getHandlerMethods());
    }

    static void check(Map<RequestMappingInfo, HandlerMethod> handlers) {
        for (var entry : handlers.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            if (info.getPathPatternsCondition() == null) {
                throw new IllegalStateException("QueryParameterContract cannot tell the paths of " + entry.getValue().getShortLogMessage()
                        + ": the mapping does not use path patterns, so the /api GET handlers could not be found");
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            boolean get = methods.isEmpty() || methods.contains(RequestMethod.GET);
            boolean api = info.getPathPatternsCondition().getPatternValues().stream().anyMatch(pattern -> pattern.startsWith("/api/"));
            if (get && api) {
                UnknownQueryParameters.declaredNames(entry.getValue());
            }
        }
    }
}
