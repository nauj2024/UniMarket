package com.atenea.unimarket.dev;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@Profile("dev")
public class DevSecurityConfig {

    @Bean
    SecurityContextRepository devSecurityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain devSecurityFilterChain(HttpSecurity http,
            SecurityContextRepository repository) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/login", "/dev/login", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(repository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .build();
    }
}
