package com.exam.httpclient.service;

import com.exam.httpclient.dto.Todo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiServiceTest {

    @Autowired
    private ApiService apiService;

    @Test
    @DisplayName("RestTemplate으로 Todo 조회")
    void testRestTemplate() {
        Todo todo = apiService.getTodoWithRestTemplate(1L);
        assertThat(todo).isNotNull();
        assertThat(todo.getId()).isEqualTo(1L);
        System.out.println("RestTemplate Result: " + todo);
    }

    @Test
    @DisplayName("WebClient로 Todo 조회")
    void testWebClient() {
        Todo todo = apiService.getTodoWithWebClient(1L);
        assertThat(todo).isNotNull();
        assertThat(todo.getId()).isEqualTo(1L);
        System.out.println("WebClient Result: " + todo);
    }

    @Test
    @DisplayName("WebClient 비동기로 Todo 조회")
    void testWebClientAsync() {
        StepVerifier.create(apiService.getTodoWithWebClientAsync(1L))
                .assertNext(todo -> {
                    assertThat(todo).isNotNull();
                    assertThat(todo.getId()).isEqualTo(1L);
                    System.out.println("WebClient Async Result: " + todo);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Http Interface로 Todo 조회")
    void testHttpInterface() {
        Todo todo = apiService.getTodoWithHttpInterface(1L);
        assertThat(todo).isNotNull();
        assertThat(todo.getId()).isEqualTo(1L);
        System.out.println("Http Interface Result: " + todo);
    }

    @Test
    @DisplayName("Http Interface 비동기로 Todo 조회")
    void testHttpInterfaceAsync() {
        StepVerifier.create(apiService.getTodoWithHttpInterfaceAsync(1L))
                .assertNext(todo -> {
                    assertThat(todo).isNotNull();
                    assertThat(todo.getId()).isEqualTo(1L);
                    System.out.println("Http Interface Async Result: " + todo);
                })
                .verifyComplete();
    }
}
