package com.github.dimitryivaniuta.profile.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * Reactive transaction infrastructure for atomic profile and outbox writes.
 */
@Configuration
public class TransactionConfig {

    /**
     * Creates a reusable transactional operator backed by the configured R2DBC transaction manager.
     *
     * @param transactionManager reactive transaction manager auto-configured for R2DBC
     * @return transactional operator used by command services
     */
    @Bean
    TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
