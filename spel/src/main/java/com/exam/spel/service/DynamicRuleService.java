package com.exam.spel.service;

import com.exam.spel.domain.User;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;

/**
 * DB나 외부에서 SpEL 표현식을 동적으로 받아 평가하는 서비스
 *
 * "누가 표현식을 작성하느냐"에 따라 EvaluationContext를 다르게 선택
 * - 개발자가 코드/어노테이션에 직접 작성 → StandardEvaluationContext
 * - 외부(DB, API, 사용자 입력)에서 동적으로 주입 → SimpleEvaluationContext
 */
@Service
public class DynamicRuleService {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * [위험] StandardEvaluationContext로 외부 표현식을 평가
     * 악의적인 표현식이 들어오면 임의 명령어 실행 가능
     */
    public Object evaluateUnsafe(String expression, User user) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("user", user);
        return parser.parseExpression(expression).getValue(context);
    }

    /**
     * [안전] SimpleEvaluationContext로 외부 표현식을 평가
     * 프로퍼티 접근과 비교 연산만 허용, 객체 생성/정적 메서드 호출 차단
     */
    public Object evaluateSafe(String expression, User user) {
        SimpleEvaluationContext context = SimpleEvaluationContext
                .forReadOnlyDataBinding()
                .withInstanceMethods()
                .build();
        context.setVariable("user", user);
        return parser.parseExpression(expression).getValue(context);
    }

    /**
     * 실무 패턴: DB에서 읽어온 규칙 표현식을 안전하게 평가
     * ex) ruleExpression = "#user.role == 'VIP'" → DB에 저장된 할인 규칙
     */
    public boolean evaluateRule(String ruleExpression, User user) {
        try {
            SimpleEvaluationContext context = SimpleEvaluationContext
                    .forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .build();
            context.setVariable("user", user);
            Boolean result = parser.parseExpression(ruleExpression).getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (SpelEvaluationException e) {
            // 잘못된 표현식은 false로 처리
            return false;
        }
    }
}
