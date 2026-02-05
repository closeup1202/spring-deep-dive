package com.project.curve.spring.outbox.config;

import com.project.curve.core.outbox.OutboxEventRepository;
import com.project.curve.spring.outbox.persistence.jpa.adapter.JpaOutboxEventRepositoryAdapter;
import com.project.curve.spring.outbox.persistence.jpa.repository.OutboxEventJpaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Configuration for enabling Outbox JPA Repository and registering Adapter beans.
 * <p>
 * Located in the same module as {@link OutboxEventJpaRepository}
 * to handle Spring Data JPA repository proxy creation.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@EnableJpaRepositories(basePackageClasses = OutboxEventJpaRepository.class)
public class OutboxJpaRepositoryConfig {

    @Bean
    public OutboxEventRepository outboxEventRepository(OutboxEventJpaRepository jpaRepository) {
        log.info("Registering OutboxEventRepository (JPA implementation)");
        return new JpaOutboxEventRepositoryAdapter(jpaRepository);
    }
}
