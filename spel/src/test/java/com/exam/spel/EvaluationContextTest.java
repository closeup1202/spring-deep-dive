package com.exam.spel;

import com.exam.spel.domain.User;
import com.exam.spel.service.DynamicRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.expression.spel.SpelEvaluationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * StandardEvaluationContext vs SimpleEvaluationContext 보안 차이 테스트
 *
 * 핵심 질문: "누가 표현식을 작성하느냐?"
 * - 개발자(어노테이션/설정) → Standard (신뢰 가능)
 * - 외부(DB, API, 사용자 입력) → Simple (차단 필요)
 */
@SpringBootTest
class EvaluationContextTest {

    @Autowired
    private DynamicRuleService ruleService;

    // --- 정상 표현식 (양쪽 모두 동작) ---

    @Test
    @DisplayName("일반 프로퍼티 접근은 Standard/Simple 모두 동작")
    void 프로퍼티_접근_정상동작() {
        User user = new User("홍길동", "VIP");

        Object unsafeResult = ruleService.evaluateUnsafe("#user.role", user);
        Object safeResult = ruleService.evaluateSafe("#user.role", user);

        assertThat(unsafeResult).isEqualTo("VIP");
        assertThat(safeResult).isEqualTo("VIP");
    }

    // --- 보안 위험 표현식 ---

    @Test
    @DisplayName("[위험] StandardEvaluationContext는 외부 표현식으로 임의 객체 생성 가능")
    void standard_외부표현식_객체생성_가능() {
        User user = new User("공격자", "USER");

        // 외부에서 악의적인 표현식이 들어온 상황
        // 실제 공격: T(Runtime).getRuntime().exec('rm -rf /') 등
        String maliciousExpression = "new String('임의 객체 생성 성공')";

        Object result = ruleService.evaluateUnsafe(maliciousExpression, user);

        // StandardEvaluationContext는 이를 허용해버림 → 위험!
        assertThat(result).isEqualTo("임의 객체 생성 성공");
    }

    @Test
    @DisplayName("[안전] SimpleEvaluationContext는 외부 표현식으로 객체 생성 시 예외 발생")
    void simple_외부표현식_객체생성_차단() {
        User user = new User("공격자", "USER");

        String maliciousExpression = "new String('임의 객체 생성 시도')";

        // SimpleEvaluationContext는 객체 생성을 차단
        assertThatThrownBy(() -> ruleService.evaluateSafe(maliciousExpression, user))
                .isInstanceOf(SpelEvaluationException.class);
    }

    @Test
    @DisplayName("[안전] SimpleEvaluationContext는 정적 메서드 호출 차단")
    void simple_정적메서드_차단() {
        User user = new User("공격자", "USER");

        // T() 연산자로 정적 메서드 접근 시도
        String maliciousExpression = "T(java.lang.Runtime).getRuntime()";

        assertThatThrownBy(() -> ruleService.evaluateSafe(maliciousExpression, user))
                .isInstanceOf(SpelEvaluationException.class);
    }

    // --- 실무 패턴: DB에서 읽어온 규칙 평가 ---

    @Test
    @DisplayName("DB에서 읽어온 할인 규칙 표현식을 안전하게 평가")
    void db_규칙_안전평가() {
        User vipUser = new User("VIP회원", "VIP");
        User normalUser = new User("일반회원", "USER");

        // DB에 저장된 할인 규칙 표현식 (관리자가 등록)
        String discountRule = "#user.role == 'VIP'";

        assertThat(ruleService.evaluateRule(discountRule, vipUser)).isTrue();
        assertThat(ruleService.evaluateRule(discountRule, normalUser)).isFalse();
    }

    @Test
    @DisplayName("잘못된 규칙 표현식은 예외 대신 false 반환")
    void 잘못된_규칙표현식_안전처리() {
        User user = new User("홍길동", "USER");

        // 잘못된 표현식이 DB에 저장된 경우
        String invalidExpression = "#user.nonExistentField == 'X'";

        // 예외를 터뜨리지 않고 false 반환
        assertThat(ruleService.evaluateRule(invalidExpression, user)).isFalse();
    }
}
