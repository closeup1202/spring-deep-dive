package com.exam.httpclient.service;

import com.exam.httpclient.dto.Todo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
    @DisplayName("Http Interface로 Todo 조회")
    void testHttpInterface() {
        Todo todo = apiService.getTodoWithHttpInterface(1L);
        assertThat(todo).isNotNull();
        assertThat(todo.getId()).isEqualTo(1L);
        System.out.println("Http Interface Result: " + todo);
    }
}
