package com.exam.httpclient.config;

import com.exam.httpclient.client.TodoClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    // 1. RestTemplate 설정 (Legacy)
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    // 2. WebClient 설정 (Modern)
    @Bean
    public WebClient webClient() {
        return WebClient.builder()
                .baseUrl("https://jsonplaceholder.typicode.com")
                .build();
    }

    // 3. HTTP Interface 설정 (Latest - Spring 6.1+ / Boot 3.2+)
    @Bean
    public TodoClient todoClient(WebClient webClient) {
        // WebClientAdapter.forClient() 대신 create() 사용
        WebClientAdapter adapter = WebClientAdapter.create(webClient);
        
        // builder()에 인자를 넘기지 않고, builderFor()를 사용하거나 설정 메서드 체이닝
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();

        return factory.createClient(TodoClient.class);
    }
}
