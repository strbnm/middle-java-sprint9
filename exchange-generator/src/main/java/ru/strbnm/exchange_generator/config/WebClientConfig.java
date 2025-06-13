package ru.strbnm.exchange_generator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.method.P;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.strbnm.exchange_generator.client.ApiClient;
import ru.strbnm.exchange_generator.client.api.ExchangeServiceApi;

@Slf4j
@Configuration
public class WebClientConfig {
    @Value("${spring.rest.exchange-service.url}")
    private String baseUrl;


    @Profile("default")
    @Bean
    public WebClient webClient(ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ExchangeFilterFunction oauth2Filter = (request, next) -> {
            OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                    .withClientRegistrationId("exchange-client")
                    .principal("exchange-generator")
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

    @Profile("test")
    @Bean
    public WebClient webClientForTest() {
        return WebClient.builder()
                .filter(logRequest())
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public ApiClient apiClient(WebClient webClient){
        // Создаем и настраиваем ApiClient
        ApiClient apiClient = new ApiClient(webClient);
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    public ExchangeServiceApi exchangeServiceApi(ApiClient apiClient) {
        return new ExchangeServiceApi(apiClient);
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

