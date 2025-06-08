package ru.strbnm.accounts_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.strbnm.accounts_service.client.notifications.ApiClient;
import ru.strbnm.accounts_service.client.notifications.api.NotificationsServiceApi;

@Slf4j
@Profile("!contracts")
@Configuration
public class NotificationsWebClientConfig {
    @Value("${spring.rest.notifications-service.url}")
    private String baseUrl;

    @Profile("default")
    @Bean("notificationsWebClient")
    public WebClient notificationsWebClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("notifications-client");

        return WebClient.builder()
                .filter(oauth2Filter)
                .baseUrl(baseUrl)
                .build();
    }

    @Profile("test")
    @Bean("notificationsWebClient")
    public WebClient webClientForTest() {
        return WebClient.builder()
                .filter(logRequest())
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("notificationsApiClient")
    public ApiClient notificationsApiClient(@Qualifier("notificationsWebClient") WebClient webClient){
        // Создаем и настраиваем ApiClient
        ApiClient apiClient = new ApiClient(webClient);
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    public NotificationsServiceApi notificationsServiceApi(@Qualifier("notificationsApiClient") ApiClient apiClient) {
        return new NotificationsServiceApi(apiClient);
    }

    // Фильтр для логирования запросов
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            // Логируем запрос
            log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers().forEach((name, values) -> {
                values.forEach(value -> log.info("{}: {}", name, value));
            });
            return Mono.just(clientRequest);
        });
    }

}

