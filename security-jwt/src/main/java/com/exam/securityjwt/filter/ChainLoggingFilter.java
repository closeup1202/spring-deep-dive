package com.exam.securityjwt.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class ChainLoggingFilter extends OncePerRequestFilter {

    private final String filterName;

    public ChainLoggingFilter(String filterName) {
        this.filterName = filterName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        log.info(">>> [{}] Start processing request: {}", filterName, request.getRequestURI());
        
        long startTime = System.currentTimeMillis();
        
        // 다음 필터로 넘김 (체인의 핵심)
        filterChain.doFilter(request, response);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("<<< [{}] End processing request. Duration: {}ms", filterName, duration);
    }
}
