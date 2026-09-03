package com.exam.jvmheap.loadtest;

import com.exam.jvmheap.memory.GcProbe;
import com.exam.jvmheap.memory.HeapProbe;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 의존성 없이 돌아가는 부하 생성기.
 *
 * <p>k6/Gatling/JMeter 를 설치하지 않아도 실습이 되도록 JDK 만으로 만들었다.
 * 실무에서는 당연히 전용 도구를 쓴다. 다만 도구가 내부에서 무엇을 하는지
 * (워커 스레드, 워밍업, 지연 수집, 백분위수 계산) 를 한 번 직접 짜보면
 * 리포트 숫자를 읽는 눈이 달라진다.
 *
 * <h3>이 구현이 지키는 것</h3>
 * <ul>
 *   <li><b>워밍업 구간을 통계에서 뺀다.</b> JIT 컴파일 전 코드는 인터프리터로 돌아 10배 이상 느리다.
 *       워밍업 없이 잰 p99 는 JVM 예열 비용을 성능 문제로 오해하게 만든다.</li>
 *   <li><b>지연을 primitive 배열에 담는다.</b> ArrayList&lt;Long&gt; 을 쓰면 측정 도구 자체가
 *       초당 수만 개의 Long 객체를 만들어 GC 를 유발한다. 관측이 대상을 바꿔버린다.</li>
 *   <li><b>측정 구간의 GC/힙 변화를 함께 기록한다.</b> p99 와 GC pause 는 같이 봐야 의미가 있다.</li>
 * </ul>
 *
 * <h3>이 구현이 못 하는 것 (전용 도구를 쓰는 이유)</h3>
 * <ul>
 *   <li>부하 생성기와 대상이 같은 머신/같은 JVM 에 있으면 CPU 를 나눠 쓴다. 숫자가 낙관적으로 나온다.</li>
 *   <li>coordinated omission 을 보정하지 않는다. 서버가 느려지면 요청 자체를 덜 보내므로
 *       실제 사용자가 겪는 지연보다 좋게 측정된다.</li>
 *   <li>램프업, 시나리오 체이닝, 분산 부하가 없다.</li>
 * </ul>
 */
public class LoadGenerator {

    private final String baseUrl;
    private final HttpClient client;

    public LoadGenerator(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public LoadResult run(LoadSpec spec) {
        // 1) 워밍업: 결과를 버린다
        if (!spec.warmup().isZero()) {
            execute(spec, spec.warmup(), false);
        }

        // 2) 측정: 이 구간의 GC/힙만 기록한다
        GcProbe.Snapshot gcBefore = GcProbe.snapshot();
        HeapProbe.Snapshot heapBefore = HeapProbe.snapshot();
        long start = System.nanoTime();

        Recording recording = execute(spec, spec.duration(), true);

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        GcProbe.Diff gc = GcProbe.snapshot().since(gcBefore);
        HeapProbe.Snapshot heapAfter = HeapProbe.snapshot();

        long[] latencies = recording.mergedLatencies();
        Arrays.sort(latencies);                    // 백분위수 계산을 위해 한 번만 정렬

        return new LoadResult(spec.name(), latencies, recording.errors.get(),
                Map.copyOf(recording.statusCounts), elapsedMillis, gc, heapBefore, heapAfter);
    }

    private Recording execute(LoadSpec spec, Duration window, boolean record) {
        Recording recording = new Recording(spec.concurrency());
        ExecutorService pool = Executors.newFixedThreadPool(spec.concurrency());
        CountDownLatch ready = new CountDownLatch(spec.concurrency());
        CountDownLatch go = new CountDownLatch(1);
        long deadline = System.nanoTime() + window.toNanos();

        for (int i = 0; i < spec.concurrency(); i++) {
            int workerIndex = i;
            pool.submit(() -> {
                LatencyBuffer buffer = recording.buffers[workerIndex];
                ready.countDown();
                try {
                    go.await();
                    HttpRequest request = buildRequest(spec);
                    while (System.nanoTime() < deadline) {
                        long t0 = System.nanoTime();
                        try {
                            HttpResponse<Void> response =
                                    client.send(request, HttpResponse.BodyHandlers.discarding());
                            long micros = (System.nanoTime() - t0) / 1000;
                            if (record) {
                                buffer.add(micros);
                                recording.statusCounts.merge(response.statusCode(), 1L, Long::sum);
                                if (response.statusCode() >= 400) {
                                    recording.errors.incrementAndGet();
                                }
                            }
                        } catch (Exception e) {
                            if (record) {
                                recording.errors.incrementAndGet();
                            }
                        }
                        if (!spec.thinkTime().isZero()) {
                            Thread.sleep(spec.thinkTime().toMillis());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        try {
            ready.await(10, TimeUnit.SECONDS);     // 모든 워커가 준비된 뒤 동시에 출발
            go.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(window.toMillis() + 30_000, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        return recording;
    }

    private HttpRequest buildRequest(LoadSpec spec) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + spec.path()))
                .timeout(Duration.ofSeconds(20));
        return switch (spec.method().toUpperCase()) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.noBody()).build();
            case "DELETE" -> builder.DELETE().build();
            default -> builder.GET().build();
        };
    }

    /** 워커별로 분리된 수집 버퍼. 공유 컬렉션에 쓰면 락 경합이 측정값을 오염시킨다. */
    private static final class Recording {
        private final LatencyBuffer[] buffers;
        private final AtomicLong errors = new AtomicLong();
        private final Map<Integer, Long> statusCounts = new ConcurrentHashMap<>();

        private Recording(int workers) {
            this.buffers = new LatencyBuffer[workers];
            for (int i = 0; i < workers; i++) {
                buffers[i] = new LatencyBuffer();
            }
        }

        private long[] mergedLatencies() {
            int total = 0;
            for (LatencyBuffer buffer : buffers) {
                total += buffer.size;
            }
            long[] merged = new long[total];
            int offset = 0;
            for (LatencyBuffer buffer : buffers) {
                System.arraycopy(buffer.values, 0, merged, offset, buffer.size);
                offset += buffer.size;
            }
            return merged;
        }
    }

    /** 박싱 없는 가변 long 배열. 측정 도구가 만드는 쓰레기를 최소화한다. */
    private static final class LatencyBuffer {
        private long[] values = new long[4096];
        private int size;

        private void add(long value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }
    }

    /** 여러 시나리오를 순서대로 돌리고 결과를 모은다. */
    public List<LoadResult> runAll(List<LoadSpec> specs) {
        List<LoadResult> results = new ArrayList<>(specs.size());
        for (LoadSpec spec : specs) {
            results.add(run(spec));
        }
        return results;
    }
}
