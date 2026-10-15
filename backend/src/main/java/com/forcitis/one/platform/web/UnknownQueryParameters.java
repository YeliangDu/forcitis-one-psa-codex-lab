package com.forcitis.one.platform.web;

import com.forcitis.one.kernel.DomainException;
import com.forcitis.one.kernel.ErrorKind;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/** A GET under /api that carries a query parameter its handler does not declare is refused by name, so a misspelt filter never reads as "no filter" (NAMING_STANDARD A-4). */
@Configuration
class UnknownQueryParameters implements WebMvcConfigurer, HandlerInterceptor {
    static final String INVALID_PARAMETERS = "invalidParameters";

    private static final int MAX_NAMED = 10;
    private static final int MAX_NAME_LENGTH = 64;
    private static final List<Class<? extends Annotation>> BINDING_ANNOTATIONS = List.of(PathVariable.class, RequestHeader.class, CookieValue.class,
            RequestBody.class, RequestAttribute.class, SessionAttribute.class, RequestPart.class, MatrixVariable.class);

    private final Map<HandlerMethod, Set<String>> declared = new ConcurrentHashMap<>();
    private final Supplier<List<HandlerMethodArgumentResolver>> resolvers;

    /** The adapter is taken when first needed: it is built from the WebMvcConfigurers, this class among them. */
    @Autowired
    UnknownQueryParameters(ObjectProvider<RequestMappingHandlerAdapter> adapter) {
        this(() -> adapter.getObject().getArgumentResolvers());
    }

    /** The resolvers in the order Spring MVC asks them. */
    UnknownQueryParameters(Supplier<List<HandlerMethodArgumentResolver>> resolvers) {
        this.resolvers = resolvers;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"GET".equals(request.getMethod()) || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        Set<String> known = declared.computeIfAbsent(method, this::declaredNames);
        Set<String> unknown = new TreeSet<>(request.getParameterMap().keySet());
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            String named = named(unknown);
            throw new DomainException(WebError.PLATFORM_WEB_REQUEST_INVALID, ErrorKind.INVALID, "unknown query parameters " + named)
                    .withExtension(INVALID_PARAMETERS, named);
        }
        return true;
    }

    /** At most MAX_NAMED names of at most MAX_NAME_LENGTH characters each; what is cut is marked with "...". */
    static String named(Set<String> unknown) {
        List<String> shown = new ArrayList<>();
        for (String name : unknown) {
            if (shown.size() == MAX_NAMED) {
                shown.add("...");
                break;
            }
            shown.add(name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) + "..." : name);
        }
        return String.join(",", shown);
    }

    /**
     * The query parameter names a GET handler reads; a parameter whose names cannot be known fails the start of the application, naming the method.
     * A parameter goes to the first resolver that supports it, as in Spring MVC: one marked @NotFromQuery is skipped, any other resolver of the
     * application may read the query and fails the start.
     */
    Set<String> declaredNames(HandlerMethod method) {
        List<HandlerMethodArgumentResolver> ordered = resolvers.get();
        Set<String> names = new TreeSet<>();
        for (MethodParameter parameter : method.getMethodParameters()) {
            parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
            Optional<HandlerMethodArgumentResolver> first = ordered.stream().filter(resolver -> resolver.supportsParameter(parameter)).findFirst();
            if (first.isPresent()) {
                Class<?> resolverType = AopUtils.getTargetClass(first.get());
                if (AnnotatedElementUtils.hasAnnotation(resolverType, NotFromQuery.class)) {
                    continue;
                }
                if (!resolverType.getName().startsWith("org.springframework.")) {
                    throw unsupported(method, parameter, "resolved by " + resolverType.getSimpleName()
                            + ", which may read the query: mark it @NotFromQuery if it does not");
                }
            }
            Class<?> type = parameter.getParameterType();
            RequestParam annotation = parameter.getParameterAnnotation(RequestParam.class);
            if (annotation != null) {
                String name = !annotation.name().isEmpty() ? annotation.name() : annotation.value();
                if (name.isEmpty() && (Map.class.isAssignableFrom(type) || MultiValueMap.class.isAssignableFrom(type))) {
                    throw unsupported(method, parameter, "@RequestParam of a Map reads every parameter");
                }
                names.add(!name.isEmpty() ? name : parameter.getParameterName());
            } else if (parameter.hasParameterAnnotation(ModelAttribute.class)) {
                throw unsupported(method, parameter, "@ModelAttribute binds an object from the query");
            } else if (hasBindingAnnotation(parameter)) {
                continue;
            } else if (parameter.getParameterAnnotations().length > 0 && BeanUtils.isSimpleProperty(type)) {
                throw unsupported(method, parameter, "a simple type with annotations but without @RequestParam: declare it with @RequestParam");
            } else if (BeanUtils.isSimpleProperty(type)) {
                names.add(parameter.getParameterName());
            } else if (ServletRequest.class.isAssignableFrom(type) || WebRequest.class.isAssignableFrom(type)) {
                throw unsupported(method, parameter, "the request itself can read any parameter");
            } else if (!Principal.class.isAssignableFrom(type) && !ServletResponse.class.isAssignableFrom(type)) {
                throw unsupported(method, parameter, "an object bound from the query (record or bean)");
            }
        }
        return names;
    }

    private static boolean hasBindingAnnotation(MethodParameter parameter) {
        return BINDING_ANNOTATIONS.stream().anyMatch(parameter::hasParameterAnnotation);
    }

    private static IllegalStateException unsupported(HandlerMethod method, MethodParameter parameter, String why) {
        return new IllegalStateException("UnknownQueryParameters cannot tell which query parameters " + method.getShortLogMessage() + " reads: parameter "
                + parameter.getParameterIndex() + " (" + parameter.getParameterType().getSimpleName() + "): " + why);
    }
}
