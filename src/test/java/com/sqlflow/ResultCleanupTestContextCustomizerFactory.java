package com.sqlflow;

import java.util.List;
import java.util.Map;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

/** Spring 테스트에서 실제 CSV 자동 정리를 공통 비활성화한다. */
public class ResultCleanupTestContextCustomizerFactory
        implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(
            Class<?> testClass,
            List<ContextConfigurationAttributes> configAttributes) {

        return CleanupDisabledCustomizer.INSTANCE;
    }

    private enum CleanupDisabledCustomizer implements ContextCustomizer {

        INSTANCE;

        @Override
        public void customizeContext(
                ConfigurableApplicationContext context,
                MergedContextConfiguration mergedConfig) {

            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "resultCleanupTestDefaults",
                            Map.<String, Object>of(
                                    "sqlflow.result.cleanup.enabled",
                                    "false")));
        }
    }
}