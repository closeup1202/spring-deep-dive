package com.exam.httpclient.client;

import com.exam.httpclient.dto.Todo;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

// @HttpExchange: 공통 경로 설정
@HttpExchange("/todos")
public interface TodoClient {

    // @GetExchange: GET 요청
    @GetExchange("/{id}")
    Todo getTodo(@PathVariable("id") Long id);

    @GetExchange
    List<Todo> getTodos();

    // @PostExchange: POST 요청
    @PostExchange
    Todo createTodo(@RequestBody Todo todo);
}
