package com.exam.spel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpelTest {

    @Test
    @DisplayName("StandardEvaluationContext는 모든 기능을 사용할 수 있어 보안 위험이 있을 수 있음")
    void standardEvaluationContextTest() {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 임의의 Java 객체 생성 및 메서드 호출 가능
        Expression expression = parser.parseExpression("new String('Hello World').toUpperCase()");
        String result = expression.getValue(context, String.class);

        assertThat(result).isEqualTo("HELLO WORLD");
    }

    @Test
    @DisplayName("SimpleEvaluationContext는 제한된 기능만 제공하여 더 안전함")
    void simpleEvaluationContextTest() {
        ExpressionParser parser = new SpelExpressionParser();
        
        // ReadOnlyDataBinding 설정으로 데이터 바인딩만 허용하고, 임의의 메서드 호출이나 객체 생성 등은 제한
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .withInstanceMethods() // 필요한 경우 인스턴스 메서드 호출 허용
                .build();

        // 객체 생성 시도 -> 예외 발생해야 함
        Expression expression = parser.parseExpression("new String('Hello World')");
        
        assertThatThrownBy(() -> expression.getValue(context))
                .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class);
    }

    @Test
    @DisplayName("SimpleEvaluationContext에서 데이터 바인딩 테스트")
    void simpleEvaluationContextDataBindingTest() {
        ExpressionParser parser = new SpelExpressionParser();
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

        Person person = new Person("John", 30);
        
        // 프로퍼티 접근은 가능
        Expression expression = parser.parseExpression("name");
        String name = expression.getValue(context, person, String.class);

        assertThat(name).isEqualTo("John");
    }

    static class Person {
        private String name;
        private int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }
}
