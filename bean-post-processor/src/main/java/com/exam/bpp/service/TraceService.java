package com.exam.bpp.service;

import com.exam.bpp.annotation.Trace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TraceService {

    @Trace
    public void slowMethod() {
        try {
            Thread.sleep(1000); // 1초 대기
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        log.info("slowMethod 실행 완료");
    }

    public void fastMethod() {
        log.info("fastMethod 실행 완료");
    }
}
