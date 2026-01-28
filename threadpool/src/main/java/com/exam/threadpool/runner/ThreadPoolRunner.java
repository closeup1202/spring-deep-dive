package com.exam.threadpool.runner;

import com.exam.threadpool.service.CustomPoolService;
import com.exam.threadpool.service.ThreadPoolService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ThreadPoolRunner implements ApplicationRunner {

    private final ThreadPoolService threadPoolService;
    private final CustomPoolService customPoolService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. Cached vs Fixed 비교
        threadPoolService.testCachedThreadPool();
        threadPoolService.testFixedThreadPool();

        Thread.sleep(1000);

        // 2. 스레드 풀 동작 순서 검증 (Core -> Queue -> Max -> Reject)
        customPoolService.testCustomPoolFlow();
    }
}