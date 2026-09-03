package com.exam.jvmheap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 부하 테스트와 JVM 힙/GC 를 함께 관찰하기 위한 학습용 애플리케이션.
 *
 * <p>핵심 아이디어: 부하를 넣지 않으면 힙 문제는 절대 보이지 않는다.
 * 그래서 이 모듈은 (1) 부하를 만드는 쪽과 (2) 힙이 어떻게 반응하는지 보는 쪽을
 * 한 프로젝트 안에 같이 둔다.
 */
@SpringBootApplication
public class JvmHeapLoadTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(JvmHeapLoadTestApplication.class, args);
    }
}
