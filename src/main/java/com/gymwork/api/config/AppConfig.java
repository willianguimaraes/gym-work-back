package com.gymwork.api.config;

import com.gymwork.api.storage.StorageProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class AppConfig {

    /** Relógio injetável: permite testar expirações sem depender da hora real. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
