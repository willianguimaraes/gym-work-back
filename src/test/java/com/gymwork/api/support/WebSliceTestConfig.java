package com.gymwork.api.support;

import com.gymwork.api.security.JwtConfig;
import com.gymwork.api.security.RefreshTokenCookie;
import com.gymwork.api.security.SecurityConfig;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Beans de segurança necessários nos testes de controller (@WebMvcTest), sem JPA. */
@TestConfiguration
@Import({SecurityConfig.class, JwtConfig.class, RefreshTokenCookie.class})
public class WebSliceTestConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
