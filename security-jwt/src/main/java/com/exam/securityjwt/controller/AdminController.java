package com.exam.securityjwt.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AdminController {

    // 일반 사용자 접근 가능
    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public String userAccess() {
        return "User Content";
    }

    // 관리자만 접근 가능
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminAccess() {
        return "Admin Content";
    }
    
    // 복합 조건 예시
    @GetMapping("/complex")
    @PreAuthorize("hasRole('ADMIN') and #username == authentication.name")
    public String complexAccess(String username) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return "Complex Content for " + auth.getName();
    }
}
