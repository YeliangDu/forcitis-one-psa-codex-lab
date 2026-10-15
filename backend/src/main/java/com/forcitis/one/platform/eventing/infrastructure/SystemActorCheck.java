package com.forcitis.one.platform.eventing.infrastructure;

import com.forcitis.one.kernel.SystemActorName;
import com.forcitis.one.kernel.SystemActors;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** Every job lock name and listener @SystemActorName is looked up in the catalogue at start, so a missing or wrong catalogue stops the start, not the first run. */
@Component
class SystemActorCheck implements SmartInitializingSingleton {
    private final ConfigurableListableBeanFactory beans;

    SystemActorCheck(ConfigurableListableBeanFactory beans) {
        this.beans = beans;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<Class<?>> types = new ArrayList<>();
        for (String name : beans.getBeanDefinitionNames()) {
            Class<?> type = beans.getType(name, false);
            if (type != null) {
                types.add(ClassUtils.getUserClass(type));
            }
        }
        check(types);
    }

    static void check(Collection<Class<?>> types) {
        List<String> undeclared = new ArrayList<>();
        for (Class<?> type : types) {
            SystemActorName actor = AnnotatedElementUtils.findMergedAnnotation(type, SystemActorName.class);
            if (actor != null) {
                lookUp(actor.value(), type.getName(), undeclared);
            }
            for (Method method : type.getDeclaredMethods()) {
                SchedulerLock lock = AnnotatedElementUtils.findMergedAnnotation(method, SchedulerLock.class);
                if (lock != null) {
                    lookUp(lock.name(), type.getName() + "." + method.getName(), undeclared);
                }
            }
        }
        if (!undeclared.isEmpty()) {
            throw new IllegalStateException("system actors not declared in " + SystemActors.CATALOGUE + ": " + undeclared);
        }
    }

    private static void lookUp(String name, String where, List<String> undeclared) {
        try {
            SystemActors.idOf(name);
        } catch (IllegalStateException notDeclared) {
            undeclared.add(name + " (" + where + ")");
        }
    }
}
