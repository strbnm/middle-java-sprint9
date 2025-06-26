package ru.strbnm.front_ui.config;

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
import ru.strbnm.front_ui.client.transfer.ApiClient;
import ru.strbnm.front_ui.client.transfer.api.TransferServiceApi;

@Slf4j
@Configuration
public class TransferServiceWebClientConfig {
    @Value("${spring.rest.transfer-service.url}")
    private String baseUrl;

    @Profile("default")
    @Bean("transferWebClient")
    public WebClient transferWebClient(
            WebClient.Builder webClientBuilder,
            ReactiveOAuth2AuthorizedClientManager authorizedClientManager) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Filter =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);

        oauth2Filter.setDefaultClientRegistrationId("transfer-client");

        return webClientBuilder
                .filter(oauth2Filter)
                .baseUrl(baseUrl)
                .build();
    }

    @Profile({"test", "contracts"})
    @Bean("transferWebClient")
    public WebClient webClientForTest() {
        return WebClient.builder()
                .filter(logRequest())
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("transferApiClient")
    public ApiClient transferApiClient(@Qualifier("transferWebClient") WebClient webClient){
        // Создаем и настраиваем ApiClient
        ApiClient apiClient = new ApiClient(webClient);
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    public TransferServiceApi transferServiceApi(@Qualifier("transferApiClient") ApiClient apiClient) {
        return new TransferServiceApi(apiClient);
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

