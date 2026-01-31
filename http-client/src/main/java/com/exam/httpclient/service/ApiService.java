package com.exam.httpclient.service;

import com.exam.httpclient.client.TodoClient;
import com.exam.httpclient.dto.Todo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiService {

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final TodoClient todoClient;

    // 1. RestTemplate 사용 (Blocking)
    public Todo getTodoWithRestTemplate(Long id) {
        log.info("Calling API with RestTemplate...");
        return restTemplate.getForObject("https://jsonplaceholder.typicode.com/todos/" + id, Todo.class);
    }

    // 2. WebClient 사용 (Non-Blocking -> Blocking 변환 예시)
    public Todo getTodoWithWebClient(Long id) {
        log.info("Calling API with WebClient...");
        return webClient.get()
                .uri("/todos/" + id)
                .retrieve()
                .bodyToMono(Todo.class)
                .block(); // 실무에서는 block()을 지양하고 Mono/Flux를 그대로 리턴하는 것이 좋음
    }

    // 2-1. WebClient 비동기 사용 (Non-Blocking)
    public Mono<Todo> getTodoWithWebClientAsync(Long id) {
        return webClient.get()
                .uri("/todos/" + id)
                .retrieve()
                .bodyToMono(Todo.class);
    }

    // 3. HTTP Interface 사용 (Declarative)
    public Todo getTodoWithHttpInterface(Long id) {
        log.info("Calling API with Http Interface...");
        return todoClient.getTodo(id);
    }
}
