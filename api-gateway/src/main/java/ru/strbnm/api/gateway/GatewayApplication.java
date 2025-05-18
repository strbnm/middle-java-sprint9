package ru.strbnm.api.gateway;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
    @Bean
    public ApplicationRunner logReactiveClients(ReactiveClientRegistrationRepository repo) {
        return args -> repo.findByRegistrationId("notification-client")
                .doOnNext(System.out::println)
                .subscribe();
    }
}