package com.example.demo;

import com.example.demo.config.LoggingConfig;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.*;

class LoggingConfigTests {
    @Test
    void injectsLoggerForEachConsumingClass() {
        new ApplicationContextRunner()
                .withUserConfiguration(LoggingConfig.class, FirstComponent.class, SecondComponent.class)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    Logger first = context.getBean(FirstComponent.class).log;
                    Logger second = context.getBean(SecondComponent.class).log;
                    assertEquals(FirstComponent.class.getName(), first.getName());
                    assertEquals(SecondComponent.class.getName(), second.getName());
                    assertNotSame(first, second);
                });
    }

    @Component
    @RequiredArgsConstructor
    static class FirstComponent {
        private final Logger log;
    }

    @Component
    @RequiredArgsConstructor
    static class SecondComponent {
        private final Logger log;
    }
}
