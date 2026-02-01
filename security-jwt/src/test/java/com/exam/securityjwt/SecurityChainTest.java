package com.exam.securityjwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityChainTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("인증 없이 보호된 리소스 접근 시 Custom EntryPoint가 동작해야 한다")
    void unauthorizedAccessTest() throws Exception {
        // When: 토큰 없이 /members/test (존재하지 않아도 됨, 인증 필터에서 걸림) 요청
        mockMvc.perform(get("/api/protected-resource"))
                .andDo(print())
                // Then: 401 Unauthorized 상태 코드와 JSON 응답 확인
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("인증에 실패했습니다. 토큰을 확인해주세요."));
    }
    
    // 로그 확인 팁:
    // 테스트 실행 후 콘솔 로그를 보면 다음과 같은 순서로 찍혀야 함
    // >>> [Filter-Start] Start processing ...
    // ... (JWT 필터 등 실행) ...
    // ... (인증 실패 Exception 발생) ...
    // <<< [Filter-Start] End processing ...
    // 주의: 인증 실패 시에는 Filter-End까지 도달하지 못하고 중간에 튕겨져 나갈 수 있음 (ExceptionTranslationFilter 때문)
}
