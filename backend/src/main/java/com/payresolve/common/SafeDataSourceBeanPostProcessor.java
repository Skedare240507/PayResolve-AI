package com.payresolve.common;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

/**
 * Spring BeanPostProcessor that validates the configured HikariDataSource
 * before it initializes or connects, enforcing fail-fast validation on DATABASE_URL
 * and registering credentials for masking.
 */
@Component
public class SafeDataSourceBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    @Override
    public int getOrder() {
        return PriorityOrdered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof HikariDataSource hikari) {
            String jdbcUrl = hikari.getJdbcUrl();
            // In tests using H2, allow H2 in-memory databases unless a postgresql URL or DATABASE_URL was supplied
            if (jdbcUrl != null && !jdbcUrl.startsWith("jdbc:h2:")) {
                DatasourceValidator.validate(jdbcUrl);
            }
            SensitiveDataMasker.registerSensitiveValue(jdbcUrl);
            SensitiveDataMasker.registerSensitiveValue(hikari.getUsername());
            SensitiveDataMasker.registerSensitiveValue(hikari.getPassword());
        }
        return bean;
    }
}

