package com.forcitis.one.platform.identity.infrastructure.security;

import com.forcitis.one.kernel.UserId;
import com.forcitis.one.kernel.CurrentUser;
import com.forcitis.one.platform.web.NotFromQuery;
import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * A controller parameter of type UserId is the signed-in caller's account, read from the session like CurrentUser (never from the request);
 * modules that may not depend on identity take it this way. A broken principal is refused as not signed in (RULING-P2-4).
 */
@Component
@NotFromQuery(source = "the session")
class CallerUserIdResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserId.class);
    }

    private final CurrentUser currentUser;

    CallerUserIdResolver(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == UserId.class && !parameter.hasParameterAnnotations();
    }

    @Override
    public UserId resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request, WebDataBinderFactory binders) {
        return currentUser.userId();
    }
}
