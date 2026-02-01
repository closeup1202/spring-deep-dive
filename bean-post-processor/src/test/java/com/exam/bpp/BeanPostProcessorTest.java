package com.exam.bpp;

import com.exam.bpp.service.TraceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BeanPostProcessorTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private TraceService traceService;

    @Test
    void testTraceAnnotation() {
        // 1. 빈이 프록시 객체인지 확인
        System.out.println("traceService class: " + traceService.getClass().getName());
        
        // Spring CGLIB 프록시는 클래스 이름에 '$$SpringCGLIB' 같은 게 붙음
        assertThat(traceService.getClass().getName()).contains("SpringCGLIB");

        // 2. 메서드 실행 (로그 확인 필요)
        System.out.println("=== slowMethod 호출 ===");
        traceService.slowMethod(); // 1초 걸리고 로그 찍혀야 함

        System.out.println("=== fastMethod 호출 ===");
        traceService.fastMethod(); // 바로 끝나고 로그 안 찍혀야 함
    }
}
