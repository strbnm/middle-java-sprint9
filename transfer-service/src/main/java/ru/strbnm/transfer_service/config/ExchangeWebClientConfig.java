package ru.strbnm.transfer_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.strbnm.transfer_service.client.exchange.ApiClient;
import ru.strbnm.transfer_service.client.exchange.api.ExchangeServiceApi;

@Slf4j
@Configuration
public class ExchangeWebClientConfig {
    @Value("${spring.rest.exchange-service.url}")
    private String baseUrl;

    @Bean("exchangeWebClient")
    public WebClient exchangeWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean("exchangeApiClient")
    public ApiClient exchangeApiClient(@Qualifier("exchangeWebClient") WebClient webClient){
        // Создаем и настраиваем ApiClient
        ApiClient apiClient = new ApiClient(webClient);
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    public ExchangeServiceApi exchangeServiceApi(@Qualifier("exchangeApiClient") ApiClient apiClient) {
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

