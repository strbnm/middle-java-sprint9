package ru.strbnm.exchange_generator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Slf4j
@Profile("!contracts & !test")
@Configuration
@EnableWebFluxSecurity
public class WebSecurityConfig {

  @Bean
  SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
    return http
            .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .anyExchange()
                    .authenticated())
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .build();
  }
}
