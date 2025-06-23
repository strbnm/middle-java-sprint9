package ru.strbnm.front_ui.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationEvent;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationEventsListener implements ApplicationListener<AbstractAuthenticationEvent> {

    private final MeterRegistry meterRegistry;

    public AuthenticationEventsListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void onApplicationEvent(AbstractAuthenticationEvent event) {
        String username = "unknown";

        if (event.getAuthentication() != null && event.getAuthentication().getName() != null) {
            username = event.getAuthentication().getName();
        }

        if (event instanceof AuthenticationSuccessEvent) {
            meterRegistry.counter("user.login.attempts", "username", username, "outcome", "success").increment();
        } else if (event instanceof AbstractAuthenticationFailureEvent) {
            meterRegistry.counter("user.login.attempts", "username", username, "outcome", "failure").increment();
        }
    }
}
