package ru.strbnm.front_ui.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class WebSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, MeterRegistry meterRegistry) {

        // Дефолтный success handler (редирект на "/")
        ServerAuthenticationSuccessHandler defaultSuccessHandler =
                new RedirectServerAuthenticationSuccessHandler("/");

        // Наш обёрнутый success handler
        ServerAuthenticationSuccessHandler wrappedSuccessHandler = (webFilterExchange, authentication) -> {
            String username = authentication.getName();
            meterRegistry.counter("custom.login", "username", username, "status", "success").increment();
            return defaultSuccessHandler.onAuthenticationSuccess(webFilterExchange, authentication);
        };

        // Дефолтный failure handler (отдаёт 401)
        ServerAuthenticationFailureHandler defaultFailureHandler =
                new RedirectServerAuthenticationFailureHandler("/login?error");

        // Обёртка
        ServerAuthenticationFailureHandler wrappedFailureHandler = (webFilterExchange, exception) -> webFilterExchange.getExchange().getFormData()
                .defaultIfEmpty(new org.springframework.util.LinkedMultiValueMap<>())
                .flatMap(data -> {
                    String username = data.getFirst("username");
                    meterRegistry.counter("custom.login", "username", username == null ? "unknown" : username, "status", "failure").increment();
                    return defaultFailureHandler.onAuthenticationFailure(webFilterExchange, exception);
                });
        http
                .oauth2Client(Customizer.withDefaults())
                .securityContextRepository(new WebSessionServerSecurityContextRepository())
                .formLogin((login) -> login
                        .authenticationSuccessHandler(wrappedSuccessHandler)
                        .authenticationFailureHandler(wrappedFailureHandler))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                )
                .authorizeExchange(exchange -> exchange
                    .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                    .pathMatchers("/", "/signup", "/css/**", "/login").permitAll()
                    .anyExchange().authenticated()
                )
                .logout(logout -> logout.logoutUrl("/logout"))
                .headers(headers -> headers.frameOptions(Customizer.withDefaults()).disable())
                .exceptionHandling(handling -> handling
                        .accessDeniedHandler((exchange, denied) ->
                                Mono.error(new AccessDeniedException("Access Denied")))
                )
        ;
        return http.build();
    }

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
}