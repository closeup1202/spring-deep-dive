package com.exam.functional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class ConsumerExampleTest {

    @Test
    @DisplayName("Consumer - accept(): 인자를 받아 처리하고 반환값이 없다")
    void basicConsumer() {
        List<String> log = new ArrayList<>();
        Consumer<String> recorder = log::add;

        recorder.accept("first");
        recorder.accept("second");

        assertThat(log).containsExactly("first", "second");
    }

    @Test
    @DisplayName("Consumer - andThen(): 체이닝 순서가 왼쪽에서 오른쪽으로 보장된다")
    void andThenOrder() {
        List<String> executionOrder = new ArrayList<>();

        Consumer<String> first = s -> executionOrder.add("first:" + s);
        Consumer<String> second = s -> executionOrder.add("second:" + s);
        Consumer<String> third = s -> executionOrder.add("third:" + s);

        Consumer<String> chain = first.andThen(second).andThen(third);
        chain.accept("X");

        assertThat(executionOrder).containsExactly("first:X", "second:X", "third:X");
    }

    @Test
    @DisplayName("Consumer - andThen(): 앞 Consumer에서 예외 발생 시 뒷 Consumer는 실행되지 않는다")
    void andThenStopsOnException() {
        List<String> log = new ArrayList<>();

        Consumer<String> failingConsumer = s -> { throw new RuntimeException("fail"); };
        Consumer<String> neverReaches = s -> log.add("reached");

        Consumer<String> chain = failingConsumer.andThen(neverReaches);

        assertThatThrownBy(() -> chain.accept("test"))
                .isInstanceOf(RuntimeException.class);
        assertThat(log).isEmpty();
    }

    @Test
    @DisplayName("Consumer 파이프라인 - buildUserPipeline 유효성 검증")
    void userPipelineValidation() {
        Consumer<ConsumerExample.User> pipeline = ConsumerExample.ConsumerChaining.buildUserPipeline();

        // 이름이 없는 경우 예외
        ConsumerExample.User invalidUser = new ConsumerExample.User("", "test@x.com", 25);
        assertThatThrownBy(() -> pipeline.accept(invalidUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이름은 필수입니다");
    }

    @Test
    @DisplayName("BiConsumer - accept(T, U): 두 인자를 받아 처리한다")
    void biConsumer() {
        List<String> log = new ArrayList<>();
        BiConsumer<String, Integer> recorder = (s, n) -> log.add(s + "=" + n);

        recorder.accept("age", 30);
        recorder.accept("score", 100);

        assertThat(log).containsExactly("age=30", "score=100");
    }

    @Test
    @DisplayName("BiConsumer - andThen(): BiConsumer도 체이닝이 가능하다")
    void biConsumerAndThen() {
        List<String> log = new ArrayList<>();

        BiConsumer<String, Integer> first = (k, v) -> log.add("first:" + k + v);
        BiConsumer<String, Integer> second = (k, v) -> log.add("second:" + k + v);

        first.andThen(second).accept("x", 1);

        assertThat(log).containsExactly("first:x1", "second:x1");
    }

    @Test
    @DisplayName("Consumer - forEach에서 Consumer를 재사용할 수 있다")
    void consumerReuse() {
        List<String> log = new ArrayList<>();
        Consumer<String> recorder = log::add;

        List<String> items = List.of("a", "b", "c");
        items.forEach(recorder); // Iterable.forEach는 Consumer를 받음

        assertThat(log).containsExactlyElementsOf(items);
    }

    @Test
    @DisplayName("Stream.peek - Consumer를 이용해 중간 요소를 관찰할 수 있다")
    void streamPeek() {
        List<String> peeked = new ArrayList<>();

        List<ConsumerExample.User> users = List.of(
                new ConsumerExample.User("Alice", "a@x.com", 20),
                new ConsumerExample.User("Bob", "b@x.com", 15),
                new ConsumerExample.User("Carol", "c@x.com", 30)
        );

        List<ConsumerExample.User> adults = users.stream()
                .peek(u -> peeked.add(u.name())) // 필터 전 전체 관찰
                .filter(u -> u.age() >= 18)
                .toList();

        assertThat(adults).hasSize(2);
        assertThat(peeked).containsExactly("Alice", "Bob", "Carol"); // 전체 관찰됨
    }
}
