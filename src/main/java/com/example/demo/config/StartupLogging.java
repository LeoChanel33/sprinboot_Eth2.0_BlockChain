package com.example.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

/** Records successful application bean initialization and application readiness. */
@Component
public class StartupLogging implements BeanPostProcessor, ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(StartupLogging.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> beanClass = ClassUtils.getUserClass(bean);
        if (beanClass.getPackageName().startsWith("com.example.demo.")
                || beanClass.getPackageName().equals("com.example.demo")) {
            log.info("Bean initialized and dependencies injected: name={}, type={}",
                    beanName, beanClass.getName());
        }
        return bean;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Application startup completed: name={}, startupTime={} ms",
                event.getApplicationContext().getEnvironment()
                        .getProperty("spring.application.name", "application"),
                event.getTimeTaken().toMillis());
    }
}
