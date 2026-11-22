package com.atenea.unimarket;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class ConfiguracionGeneral {

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
