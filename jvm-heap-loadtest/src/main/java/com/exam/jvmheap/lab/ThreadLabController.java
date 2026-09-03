package com.exam.jvmheap.lab;

import com.exam.jvmheap.memory.Bytes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 느린 엔드포인트. 부하 테스트에서 스레드 고갈과 "동시성 x 요청당 메모리" 를 보여준다.
 *
 * <p>리틀의 법칙(Little law): 필요한 동시 처리 수 = 도착률(TPS) x 평균 응답시간(초).
 * 200 TPS x 0.5s = 동시 100건. Tomcat 스레드가 20개면 나머지는 큐에서 기다린다.
 * 이때 응답시간은 "처리시간 + 대기시간" 이 되어 급격히 무너진다.
 *
 * <p>동시에, 요청 하나가 1MB 버퍼를 잡고 있으면 동시 100건 = 100MB 가 힙에 상주한다.
 * 부하가 힙을 밀어올리는 가장 흔한 경로다.
 */
@RestController
@RequestMapping("/lab")
public class ThreadLabController {

    private final AtomicInteger inFlight = new AtomicInteger();

    @GetMapping("/blocking")
    public Map<String, Object> blocking(@RequestParam(defaultValue = "500") long millis,
                                        @RequestParam(defaultValue = "1024") int bufferKb) throws InterruptedException {
        int concurrent = inFlight.incrementAndGet();
        try {
            // 요청이 살아있는 동안 유지되는 버퍼. 동시 요청 수에 비례해 힙을 먹는다.
            byte[] buffer = new byte[bufferKb * 1024];
            buffer[0] = 1;

            Thread.sleep(millis);                 // 외부 API 호출이나 느린 쿼리를 흉내낸다

            return Map.of(
                    "thread", Thread.currentThread().getName(),
                    "concurrentInFlight", concurrent,
                    "retainedPerRequest", Bytes.human(buffer.length),
                    "estimatedRetained", Bytes.human((long) concurrent * buffer.length)
            );
        } finally {
            inFlight.decrementAndGet();
        }
    }
}
