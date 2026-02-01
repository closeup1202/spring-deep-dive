//package com.exam.vthread;
//
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.web.server.LocalServerPort;
//import org.springframework.web.client.RestClient;
//
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicInteger;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//class VirtualThreadLoadTest {
//
//    @LocalServerPort
//    private int port;
//
//    @Test
//    @DisplayName("1000개의 동시 요청을 가상 스레드로 처리한다")
//    void loadTest() throws InterruptedException {
//        int numberOfRequests = 1000;
//        // 클라이언트 측에서는 가상 스레드를 써서 요청을 팍팍 보냄
//        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
//        AtomicInteger successCount = new AtomicInteger();
//
//        RestClient restClient = RestClient.create();
//        long startTime = System.currentTimeMillis();
//
//        for (int i = 0; i < numberOfRequests; i++) {
//            executor.submit(() -> {
//                try {
//                    restClient.get()
//                            .uri("http://localhost:" + port + "/block")
//                            .retrieve()
//                            .toBodilessEntity();
//                    successCount.incrementAndGet();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            });
//        }
//
//        executor.shutdown();
//        // 1000개 요청이 다 끝날 때까지 대기 (최대 10초)
//        // 서버가 1초씩 자니까, 기존 스레드풀(200개)이었다면 최소 5초 이상 걸림
//        // 가상 스레드라면 거의 1초 + @ 만에 끝남
//        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
//
//        long endTime = System.currentTimeMillis();
//        long duration = endTime - startTime;
//
//        System.out.println("=== Test Result ===");
//        System.out.println("Total Requests: " + numberOfRequests);
//        System.out.println("Success Count: " + successCount.get());
//        System.out.println("Total Duration: " + duration + "ms");
//
//        assertThat(successCount.get()).isEqualTo(numberOfRequests);
//        // 1000개 요청을 처리하는데 2초 미만이어야 함 (이론상 1초 조금 넘음)
//        assertThat(duration).isLessThan(2000);
//    }
//}
