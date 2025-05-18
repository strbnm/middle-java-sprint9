package ru.strbnm.api.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Configuration
public class CustomSecurityContextFilter {

    @Value("${spring.application.name}")
    private String applicationName;

    @Bean
    public WebFilter customAuthenticationWebFilter() {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .switchIfEmpty(Mono.defer(() -> {
                    // Создаем кастомный Authentication, если контекста нет вообще
                    Authentication auth = buildCustomAuthentication();
                    return Mono.just(new SecurityContextImpl(auth));
                }))
                .flatMap(ctx -> {
                    Authentication existingAuth = ctx.getAuthentication();
                    if (existingAuth != null && existingAuth.isAuthenticated()) {
                        // Уже есть Authentication — пропускаем без изменений
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx)));
                    } else {
                        // Нет аутентификации — добавим кастомную
                        Authentication auth = buildCustomAuthentication();
                        SecurityContext newCtx = new SecurityContextImpl(auth);
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(newCtx)));
                    }
                });
    }

    private Authentication buildCustomAuthentication() {
        return new AbstractAuthenticationToken(Collections.emptyList()) {
            @Override
            public Object getCredentials() {
                return "";
            }

            @Override
            public Object getPrincipal() {
                return applicationName;
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }
        };
    }
}
