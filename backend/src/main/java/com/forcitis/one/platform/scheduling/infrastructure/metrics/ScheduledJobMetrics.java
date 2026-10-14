package com.forcitis.one.platform.scheduling.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.ClassUtils;

/**
 * Counts the runs of every scheduled job that ended in an exception, tagged with the job name (its @SchedulerLock name, G46). A job that
 * is skipped because another node holds its lock throws nothing and is not counted. The advisor is outermost, so a run that never
 * started because the lock store failed (a LockException from the lock library) is counted too: for the alert it is a job that did not run.
 */
@Configuration
class ScheduledJobMetrics implements ApplicationListener<ContextRefreshedEvent> {
    private final MeterRegistry registry;

    ScheduledJobMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Static and handed a provider, so building the advisor early (it must exist before the first bean is proxied) does not create the registry early. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor scheduledJobFailureAdvisor(ObjectProvider<MeterRegistry> registry) {
        Advice countFailures = (MethodInterceptor) invocation -> {
            try {
                return invocation.proceed();
            } catch (Throwable failure) {
                registry.getObject().counter("forcitis.platform.scheduling.job.failures", "job", jobName(invocation.getMethod())).increment();
                throw failure;
            }
        };
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(new AnnotationMatchingPointcut(null, SchedulerLock.class, true), countFailures);
        advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return advisor;
    }

    /** A counter that does not exist yet cannot be rated: every scheduled job starts at zero so its first failure is visible. */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent refreshed) {
        ApplicationContext context = refreshed.getApplicationContext();
        for (String bean : context.getBeanNamesForType(Object.class, false, false)) {
            Class<?> type = context.getType(bean, false);
            if (type == null) {
                continue;
            }
            MethodIntrospector.selectMethods(ClassUtils.getUserClass(type), (MethodIntrospector.MetadataLookup<String>) method ->
                    AnnotatedElementUtils.hasAnnotation(method, Scheduled.class) && AnnotatedElementUtils.hasAnnotation(method, SchedulerLock.class)
                            ? jobName(method) : null)
                    .values().forEach(job -> registry.counter("forcitis.platform.scheduling.job.failures", "job", job));
        }
    }

    static String jobName(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, SchedulerLock.class).name();
    }
}
