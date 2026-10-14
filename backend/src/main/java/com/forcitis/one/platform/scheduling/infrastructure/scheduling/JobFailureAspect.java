package com.forcitis.one.platform.scheduling.infrastructure.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Every job that holds a lock reports its failure the same way: an ALERT log line and forcitis.platform.scheduling.job.failures tagged with the job name. */
@Aspect
@Component
class JobFailureAspect {
    private static final Logger LOG = LoggerFactory.getLogger(JobFailureAspect.class);

    private final MeterRegistry meters;

    JobFailureAspect(MeterRegistry meters) {
        this.meters = meters;
    }

    @Around("@annotation(net.javacrumbs.shedlock.spring.annotation.SchedulerLock)")
    Object reportFailure(ProceedingJoinPoint job) throws Throwable {
        try {
            return job.proceed();
        } catch (Throwable failure) {
            String name = ((MethodSignature) job.getSignature()).getMethod().getAnnotation(SchedulerLock.class).name();
            LOG.error("ALERT job {} failed: {}", name, failure.toString(), failure);
            meters.counter("forcitis.platform.scheduling.job.failures", "job", name).increment();
            throw failure;
        }
    }
}
