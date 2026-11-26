package com.gymwork.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
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
