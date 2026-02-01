package com.exam.mvc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ArgumentResolverTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("헤더에 ID가 있으면 UserSession 객체가 주입된다")
    void resolveArgumentWithHeader() throws Exception {
        mockMvc.perform(get("/me")
                        .header("X-User-Id", "123"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, user123! (ID: 123)"));
    }

    @Test
    @DisplayName("헤더가 없으면 null이 주입된다")
    void resolveArgumentWithoutHeader() throws Exception {
        mockMvc.perform(get("/me"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("Guest User"));
    }
}
