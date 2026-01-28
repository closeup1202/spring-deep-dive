package com.exam.securityjwt.controller;

import com.exam.securityjwt.jwt.JwtTokenProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> user) {
        String username = user.get("username");
        // 실제로는 DB에서 사용자의 권한을 조회해야 함
        // 예제를 위해 username이 "admin"이면 ADMIN 권한, 아니면 USER 권한 부여
        String role = "admin".equals(username) ? "ROLE_ADMIN" : "ROLE_USER";
        
        return jwtTokenProvider.createToken(username, role);
    }
}
