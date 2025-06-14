package ru.strbnm.cash_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.strbnm.cash_service.client.notifications.ApiClient;
import ru.strbnm.cash_service.client.notifications.api.NotificationsServiceApi;

@Slf4j
@Profile("!contracts")
@Configuration
public class NotificationsWebClientConfig {
    @Value("${spring.rest.notifications-service.url}")
    private String baseUrl;

    @Profile("default")
    @Bean("notificationsWebClient")
    public WebClient notificationsWebClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ExchangeFilterFunction oauth2Filter = (request, next) -> {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId("notification-client")
                    .principal("cash-service")
                    .build();

            return authorizedClientManager.authorize(authorizeRequest)
                    .flatMap(client -> {
                        ClientRequest filteredRequest = ClientRequest.from(request)
                                .header(HttpHeaders.AUTHORIZATION,
                                        "Bearer " + client.getAccessToken().getTokenValue())
                                .build();
                        return next.exchange(filteredRequest);
                    });
        };

        return WebClient.builder()
                .filter(oauth2Filter)
                .baseUrl(baseUrl)
                .build();
    }

    @Profile({"test"})
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

