package com.exam.mvc.resolver;

import com.exam.mvc.annotation.LoginUser;
import com.exam.mvc.domain.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Slf4j
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    // 1. 이 Resolver가 지원하는 파라미터인지 확인
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean hasAnnotation = parameter.hasParameterAnnotation(LoginUser.class);
        boolean hasType = UserSession.class.isAssignableFrom(parameter.getParameterType());
        
        // @LoginUser 어노테이션이 있고, 타입이 UserSession인 경우에만 동작
        return hasAnnotation && hasType;
    }

    // 2. 실제 파라미터에 들어갈 객체를 생성하여 반환
    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        
        // 예제: 헤더에서 사용자 ID를 가져온다고 가정 (실무에선 JWT 토큰 파싱 등)
        String userIdStr = request.getHeader("X-User-Id");
        
        if (userIdStr == null) {
            // 로그인하지 않은 사용자 처리 (null 반환하거나 예외 발생)
            log.warn("X-User-Id header is missing");
            return null; 
            // 또는 throw new UnauthorizedException("로그인이 필요합니다.");
        }

        log.info("Resolving argument for user id: {}", userIdStr);
        
        // DB 조회 등을 통해 사용자 정보를 구성
        return new UserSession(Long.parseLong(userIdStr), "user" + userIdStr, "ROLE_USER");
    }
}
