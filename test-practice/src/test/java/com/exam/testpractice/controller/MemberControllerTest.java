package com.exam.testpractice.controller;

import com.exam.testpractice.domain.Member;
import com.exam.testpractice.service.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class) // Controller만 로드
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean // Spring Context에 가짜 Bean 등록
    private MemberService memberService;

    @Test
    @DisplayName("회원가입 API 성공 테스트")
    void signupApiTest() throws Exception {
        // given
        String email = "api@test.com";
        String name = "ApiTester";
        
        given(memberService.signup(email, name)).willReturn(new Member(10L, email, name));

        // when & then
        mockMvc.perform(post("/members")
                        .param("email", email)
                        .param("name", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @DisplayName("회원 조회 API 테스트")
    void getMemberApiTest() throws Exception {
        // given
        Long id = 1L;
        given(memberService.getMember(id)).willReturn(new Member(id, "user@test.com", "User"));

        // when & then
        mockMvc.perform(get("/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("User"));
    }
}
