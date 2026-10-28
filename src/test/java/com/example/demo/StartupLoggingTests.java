package com.example.demo;

import com.example.demo.config.StartupLogging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class StartupLoggingTests {
    @Test
    void logsInitializedBeansAndApplicationReadiness(CapturedOutput output) {
        new ApplicationContextRunner()
                .withUserConfiguration(StartupLogging.class)
                .withPropertyValues("spring.application.name=startup-test")
                .withBean("sampleBean", SampleBean.class)
                .run(context -> {
                    assertTrue(context.getBean(SampleBean.class).initialized);
                    assertTrue(output.getOut().contains(
                            "Bean initialized and dependencies injected: name=sampleBean"));
                    context.publishEvent(new ApplicationReadyEvent(
                            new SpringApplication(Application.class), new String[0],
                            context.getSourceApplicationContext(), Duration.ofMillis(1234)));
                    assertTrue(output.getOut().contains(
                            "Application startup completed: name=startup-test, startupTime=1234 ms"));
                });
    }

    static class SampleBean implements InitializingBean {
        boolean initialized;

        @Override
        public void afterPropertiesSet() {
            initialized = true;
        }
    }
}
