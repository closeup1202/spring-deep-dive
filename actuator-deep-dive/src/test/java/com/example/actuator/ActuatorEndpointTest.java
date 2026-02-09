package com.example.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Actuator 엔드포인트 테스트
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Health 엔드포인트는 인증 없이 접근 가능
     */
    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    /**
     * Info 엔드포인트는 인증 없이 접근 가능
     */
    @Test
    void testInfoEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("Actuator Deep Dive"));
    }

    /**
     * Metrics 엔드포인트는 인증 필요
     */
    @Test
    void testMetricsEndpoint_Unauthorized() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andDo(print())
                .andExpect(status().isUnauthorized());
    }

    /**
     * ADMIN 권한으로 Metrics 접근
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void testMetricsEndpoint_Authorized() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    /**
     * 커스텀 엔드포인트 테스트
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void testCustomEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/custom"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationName").value("Actuator Deep Dive"))
                .andExpect(jsonPath("$.version").value("1.0.0"));
    }

    /**
     * 캐시 통계 엔드포인트 테스트
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void testCacheStatsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/cache-stats"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products").exists())
                .andExpect(jsonPath("$.users").exists());
    }

    /**
     * 특정 캐시 통계 조회
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void testCacheStatsEndpoint_Specific() throws Exception {
        mockMvc.perform(get("/actuator/cache-stats/products"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheName").value("products"))
                .andExpect(jsonPath("$.hitRate").exists());
    }

    /**
     * 주문 생성 후 메트릭 확인
     */
    @Test
    @WithMockUser(roles = "ADMIN")
    void testOrderMetrics() throws Exception {
        // 주문 생성
        mockMvc.perform(post("/api/orders")
                        .contentType("application/json")
                        .content("{\"category\":\"electronics\",\"amount\":50000}"))
                .andExpect(status().isOk());

        // 메트릭 확인
        mockMvc.perform(get("/actuator/metrics/orders.created"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("orders.created"))
                .andExpect(jsonPath("$.measurements[0].value").exists());
    }
}
