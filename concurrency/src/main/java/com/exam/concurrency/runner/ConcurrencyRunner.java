package com.exam.concurrency.runner;

import com.exam.concurrency.service.WorkerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConcurrencyRunner implements ApplicationRunner {

    private final WorkerService workerService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 5명의 워커를 동시에 실행시키고, 모두 끝날 때까지 대기
        workerService.executeWorkers(5);
    }
}