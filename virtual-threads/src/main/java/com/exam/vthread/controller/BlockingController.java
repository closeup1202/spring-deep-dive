package com.exam.vthread.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class BlockingController {

    @GetMapping("/block")
    public String block() throws InterruptedException {
        // 현재 스레드 정보 출력
        // 가상 스레드라면 "VirtualThread[#...]" 형태로 출력됨
        log.info("Request handling start. Thread: {}", Thread.currentThread());

        // 1초간 블로킹 (DB 조회나 외부 API 호출 시뮬레이션)
        // 가상 스레드는 이때 실제 OS 스레드(Carrier Thread)를 놓아주고 대기 상태로 들어감
        Thread.sleep(1000);

        log.info("Request handling end. Thread: {}", Thread.currentThread());
        return "OK";
    }
}
