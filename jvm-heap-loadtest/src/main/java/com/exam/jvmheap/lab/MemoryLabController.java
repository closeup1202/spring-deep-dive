package com.exam.jvmheap.lab;

import com.exam.jvmheap.memory.Bytes;
import com.exam.jvmheap.memory.GcProbe;
import com.exam.jvmheap.memory.HeapProbe;
import com.exam.jvmheap.memory.LeakySessionStore;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 힙을 일부러 망가뜨리는 실습 엔드포인트.
 *
 * <p>세 가지를 구분해서 보는 것이 이 모듈의 목표다.
 * <ol>
 *   <li><b>할당량이 많은 것</b>(/lab/alloc) - GC 가 자주 돌 뿐, 힙은 회수된다. 대개 문제 아님</li>
 *   <li><b>누수</b>(/lab/leak) - 회수가 안 된다. GC 를 돌려도 Old 가 내려오지 않는다</li>
 *   <li><b>큰 객체 한 방</b>(/lab/humongous) - 총량은 남아도 단일 할당이 실패할 수 있다</li>
 * </ol>
 */
@RestController
@RequestMapping("/lab")
public class MemoryLabController {

    private final LeakySessionStore leakySessionStore;

    public MemoryLabController(LeakySessionStore leakySessionStore) {
        this.leakySessionStore = leakySessionStore;
    }

    /** 현재 힙/GC 상태. actuator 없이도 바로 볼 수 있게 만들어 둔다. */
    @GetMapping("/heap")
    public Map<String, Object> heap() {
        HeapProbe.Snapshot heap = HeapProbe.snapshot();

        Map<String, Object> pools = new LinkedHashMap<>();
        heap.pools().forEach((name, usage) -> pools.put(name, Map.of(
                "used", Bytes.human(usage.used()),
                "committed", Bytes.human(usage.committed()),
                "max", usage.max() < 0 ? "unbounded" : Bytes.human(usage.max())
        )));

        return Map.of(
                "used", Bytes.human(heap.used()),
                "committed", Bytes.human(heap.committed()),
                "max", Bytes.human(heap.max()),
                "usedRatioOfMax", "%.1f%%".formatted(heap.usedRatioOfMax() * 100),
                "pools", pools,
                "gc", GcProbe.snapshot().collectors()
        );
    }

    /**
     * 누수. 호출할 때마다 살아있는 객체가 늘고, 절대 줄지 않는다.
     *
     * <p>-Xmx64m 으로 띄운 뒤 몇 번만 호출하면 OutOfMemoryError 가 난다.
     */
    @PostMapping("/leak")
    public Map<String, Object> leak(@RequestParam(defaultValue = "200") int count,
                                    @RequestParam(defaultValue = "50") int payloadKb) {
        for (int i = 0; i < count; i++) {
            leakySessionStore.put(payloadKb);
        }
        HeapProbe.Snapshot heap = HeapProbe.snapshot();
        return Map.of(
                "sessions", leakySessionStore.size(),
                "retained", Bytes.human(leakySessionStore.approximateBytes()),
                "heapUsed", Bytes.human(heap.used()),
                "usedRatioOfMax", "%.1f%%".formatted(heap.usedRatioOfMax() * 100)
        );
    }

    /** 누수를 끊는다. 이 호출 뒤에 GC 가 돌면 Old 가 내려온다. */
    @DeleteMapping("/leak")
    public Map<String, Object> clearLeak() {
        int before = leakySessionStore.size();
        leakySessionStore.clear();
        return Map.of("cleared", before, "sessions", leakySessionStore.size());
    }

    /**
     * 할당 폭탄. 만들자마자 버린다.
     *
     * <p>GC 횟수는 폭증하지만 힙 사용량은 제자리로 돌아온다.
     * "GC 가 많이 돈다 = 누수" 가 아니라는 것을 확인하는 곳이다.
     */
    @PostMapping("/alloc")
    public Map<String, Object> alloc(@RequestParam(defaultValue = "100") int totalMb,
                                     @RequestParam(defaultValue = "16") int chunkKb) {
        GcProbe.Snapshot gcBefore = GcProbe.snapshot();
        HeapProbe.Snapshot heapBefore = HeapProbe.snapshot();
        long start = System.nanoTime();

        long target = Bytes.mb(totalMb);
        long allocated = 0;
        byte[] sink = new byte[8];                // JIT 가 할당 자체를 지우지 못하도록 결과를 남긴다
        while (allocated < target) {
            sink = new byte[chunkKb * 1024];
            sink[0] = 1;
            allocated += sink.length;
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        GcProbe.Diff gc = GcProbe.snapshot().since(gcBefore);
        HeapProbe.Snapshot heapAfter = HeapProbe.snapshot();

        return Map.of(
                "allocated", Bytes.human(allocated),
                "lastChunkBytes", sink.length,
                "elapsedMs", elapsedMs,
                "gcCollections", gc.collections(),
                "gcPauseMs", gc.pauseMillis(),
                "gcPauseRatio", "%.1f%%".formatted(gc.pauseRatio(elapsedMs) * 100),
                "heapUsedBefore", Bytes.human(heapBefore.used()),
                "heapUsedAfter", Bytes.human(heapAfter.used())
        );
    }

    /**
     * 거대 객체 한 방.
     *
     * <p>G1 에서 region 크기의 절반을 넘는 배열은 humongous 로 분류돼 연속된 region 을 요구한다.
     * 힙에 여유 총량이 있어도 연속된 자리가 없으면 실패한다. 이것이 힙 단편화다.
     * 대용량 파일을 통째로 byte 배열로 읽는 코드가 운영에서 터지는 전형적인 이유.
     */
    @PostMapping("/humongous")
    public Map<String, Object> humongous(@RequestParam(defaultValue = "32") int sizeMb) {
        try {
            byte[] big = new byte[(int) Bytes.mb(sizeMb)];
            big[0] = 1;
            return Map.of("ok", true, "allocated", Bytes.human(big.length));
        } catch (OutOfMemoryError e) {
            // OOM 을 잡는 것은 원칙적으로 금기다. 여기서는 실습을 이어가기 위해서만 잡는다.
            return Map.of("ok", false, "error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
