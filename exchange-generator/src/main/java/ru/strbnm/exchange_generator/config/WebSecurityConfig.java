package ru.strbnm.exchange_generator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Slf4j
@Profile("!contracts & !test")
@Configuration
@EnableWebFluxSecurity
public class WebSecurityConfig {

  @Profile("default")
  @Bean
  public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
          ReactiveClientRegistrationRepository clientRegistrationRepository,
          ServerOAuth2AuthorizedClientRepository authorizedClientRepository
  ) {
    ReactiveOAuth2AuthorizedClientProvider authorizedClientProvider =
            ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .refreshToken()
                    .build();

    DefaultReactiveOAuth2AuthorizedClientManager authorizedClientManager =
            new DefaultReactiveOAuth2AuthorizedClientManager(
                    clientRegistrationRepository, authorizedClientRepository);

    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }

  @Bean
  SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
    return http
            .oauth2Client(Customizer.withDefaults())
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
