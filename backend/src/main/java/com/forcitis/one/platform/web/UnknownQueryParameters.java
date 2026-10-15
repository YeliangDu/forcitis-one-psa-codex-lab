package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** A GET under /api that carries a query parameter its handler does not declare is refused by name, so a misspelt filter never reads as "no filter" (NAMING_STANDARD A-4). */
@Configuration
class UnknownQueryParameters implements WebMvcConfigurer, HandlerInterceptor {
    static final String INVALID_PARAMETERS = "invalidParameters";

    private final Map<HandlerMethod, Set<String>> declared = new ConcurrentHashMap<>();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equals(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        Set<String> known = declared.computeIfAbsent(method, UnknownQueryParameters::declaredNames);
        Set<String> unknown = new TreeSet<>(request.getParameterMap().keySet());
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw new DomainException(WebError.PLATFORM_WEB_REQUEST_INVALID, ErrorKind.INVALID, "unknown query parameters " + unknown)
                    .withExtension(INVALID_PARAMETERS, String.join(",", unknown));
        }
        return true;
    }

    private static Set<String> declaredNames(HandlerMethod method) {
        Set<String> names = new TreeSet<>();
        for (MethodParameter parameter : method.getMethodParameters()) {
            RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
            if (annotation != null) {
                parameter.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
                String name = !annotation.name().isEmpty() ? annotation.name() : annotation.value();
                names.add(!name.isEmpty() ? name : parameter.getParameterName());
            } else if (parameter.getParameterAnnotations().length == 0 && BeanUtils.isSimpleProperty(parameter.getParameterType())) {
                parameter.initParameterNameDiscovery(new org.springframework.core.DefaultParameterNameDiscoverer());
                names.add(parameter.getParameterName());
            }
        }
        return names;
    }
}
