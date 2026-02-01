package com.exam.securityjwt.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;
import java.util.Map;

// 4. AbstractAuthenticationProcessingFilter: 로그인 처리 전용 필터
// - 컨트롤러까지 가지 않고 필터에서 인증을 완료하고 토큰을 발급할 때 사용
public class JsonLoginFilter extends AbstractAuthenticationProcessingFilter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonLoginFilter(String defaultFilterProcessesUrl, AuthenticationManager authenticationManager) {
        // 1. 어떤 URL 요청을 처리할지 설정 (예: POST /api/login)
        super(new AntPathRequestMatcher(defaultFilterProcessesUrl, "POST"));
        // 2. 인증을 수행할 매니저 설정
        setAuthenticationManager(authenticationManager);
    }

    // 3. 인증 시도 로직 (attemptAuthentication)
    // - request에서 ID/PW를 꺼내서 AuthenticationManager에게 던짐
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        // JSON 파싱
        Map<String, String> loginData = objectMapper.readValue(request.getInputStream(), Map.class);
        String username = loginData.get("username");
        String password = loginData.get("password");

        // 인증 토큰 생성 (아직 인증 안됨)
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(username, password);

        // 매니저에게 검증 요청 -> Provider가 비밀번호 확인 -> 성공 시 인증된 Authentication 객체 반환
        return getAuthenticationManager().authenticate(authRequest);
    }

    // 4. 인증 성공 시 실행 (successfulAuthentication)
    // - 여기서 JWT를 발급해서 응답 헤더나 바디에 넣어주면 됨
    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException, ServletException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\": \"Login Success\", \"user\": \"" + authResult.getName() + "\"}");
        // 실제로는 여기서 JwtTokenProvider를 써서 토큰을 발급함
    }
}
