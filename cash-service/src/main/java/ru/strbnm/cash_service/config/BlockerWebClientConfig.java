package ru.strbnm.cash_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.strbnm.cash_service.client.blocker.ApiClient;
import ru.strbnm.cash_service.client.blocker.api.BlockerServiceApi;

@Slf4j
@Configuration
public class BlockerWebClientConfig {
    @Value("${spring.rest.blocker-service.url}")
    private String baseUrl;

    @Bean("blockerWebClient")
    public WebClient blockerWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("blockerApiClient")
    public ApiClient blockerApiClient(@Qualifier("blockerWebClient") WebClient webClient){
        // Создаем и настраиваем ApiClient
        ApiClient apiClient = new ApiClient(webClient);
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    public BlockerServiceApi blockerServiceApi(@Qualifier("blockerApiClient") ApiClient apiClient) {
        return new BlockerServiceApi(apiClient);
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

