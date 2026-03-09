package com.exam.sessioncookie.session.advanced;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 세션 보안 고급 기능
 * - 동시 로그인 제한 (Max Session Control)
 * - 세션 고정 공격 방어 (Session Fixation)
 * - 세션 하이재킹 탐지
 */
@Slf4j
@RestController
@RequestMapping("/api/session/security")
@RequiredArgsConstructor
@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
public class SessionSecurityController {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    /**
     * 1. 동시 로그인 제한 (Concurrent Session Control)
     * - 한 사용자가 최대 N개의 세션만 유지 가능
     */
    @PostMapping("/login-with-limit")
    public Map<String, Object> loginWithSessionLimit(
            HttpServletRequest request,
            @RequestParam String username,
            @RequestParam(defaultValue = "2") int maxSessions) {

        // 기존 세션 조회
        Map<String, ? extends Session> existingSessions = sessionRepository.findByPrincipalName(username);

        // 최대 세션 수 초과 시 가장 오래된 세션 제거
        if (existingSessions.size() >= maxSessions) {
            // 가장 오래된 세션 찾기
            String oldestSessionId = existingSessions.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().getLastAccessedTime()))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            if (oldestSessionId != null) {
                sessionRepository.deleteById(oldestSessionId);
                log.warn("Oldest session removed due to limit: sessionId={}, username={}",
                        oldestSessionId, username);
            }
        }

        // 새 세션 생성
        HttpSession session = request.getSession(true);
        session.setAttribute("username", username);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
        session.setAttribute("loginTime", System.currentTimeMillis());

        log.info("User logged in: username={}, sessionId={}, activeSessions={}",
                username, session.getId(), existingSessions.size() + 1);

        return Map.of(
                "sessionId", session.getId(),
                "username", username,
                "maxSessions", maxSessions,
                "currentActiveSessions", existingSessions.size() + 1,
                "message", "로그인 성공"
        );
    }

    /**
     * 2. 세션 고정 공격 방어 (Session Fixation Protection)
     * - 로그인 시 새로운 세션 ID 발급
     */
    @PostMapping("/login-secure")
    public Map<String, Object> loginWithSessionFixationProtection(
            HttpServletRequest request,
            @RequestParam String username) {

        // 기존 세션 ID 저장
        HttpSession oldSession = request.getSession(false);
        String oldSessionId = oldSession != null ? oldSession.getId() : "없음";

        // 기존 세션 무효화 (있다면)
        if (oldSession != null) {
            oldSession.invalidate();
            log.info("Old session invalidated: {}", oldSessionId);
        }

        // 새 세션 생성 (새로운 세션 ID)
        HttpSession newSession = request.getSession(true);
        newSession.setAttribute("username", username);
        newSession.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
        newSession.setAttribute("loginTime", System.currentTimeMillis());

        log.info("New session created for security: oldId={}, newId={}", oldSessionId, newSession.getId());

        return Map.of(
                "oldSessionId", oldSessionId,
                "newSessionId", newSession.getId(),
                "username", username,
                "message", "세션 고정 공격 방어: 새로운 세션 ID 발급"
        );
    }

    /**
     * 3. 세션 하이재킹 탐지 (User-Agent / IP 검증)
     */
    @PostMapping("/validate-session")
    public Map<String, Object> validateSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session == null) {
            return Map.of("valid", false, "message", "세션이 없습니다.");
        }

        // 세션 생성 시 저장한 정보
        String storedUserAgent = (String) session.getAttribute("userAgent");
        String storedIpAddress = (String) session.getAttribute("ipAddress");

        // 현재 요청 정보
        String currentUserAgent = request.getHeader("User-Agent");
        String currentIpAddress = request.getRemoteAddr();

        boolean isValid = true;
        List<String> warnings = new ArrayList<>();

        // User-Agent 검증
        if (storedUserAgent != null && !storedUserAgent.equals(currentUserAgent)) {
            isValid = false;
            warnings.add("User-Agent 불일치: 세션 하이재킹 의심");
            log.warn("Session hijacking suspected: Different User-Agent. SessionId={}", session.getId());
        }

        // IP 주소 검증 (선택사항 - 모바일 환경에서는 IP가 자주 변경됨)
        if (storedIpAddress != null && !storedIpAddress.equals(currentIpAddress)) {
            warnings.add("IP 주소 변경 감지: " + storedIpAddress + " -> " + currentIpAddress);
            log.warn("IP address changed. SessionId={}, old={}, new={}",
                    session.getId(), storedIpAddress, currentIpAddress);
        }

        if (!isValid) {
            // 의심스러운 세션은 무효화
            session.invalidate();
            log.warn("Session invalidated due to security concerns");
        }

        return Map.of(
                "valid", isValid,
                "warnings", warnings,
                "sessionId", session.getId(),
                "message", isValid ? "세션이 유효합니다." : "보안 위협 감지: 세션 무효화됨"
        );
    }

    /**
     * 4. 보안 세션 생성 (User-Agent, IP 저장)
     */
    @PostMapping("/create-secure")
    public Map<String, Object> createSecureSession(
            HttpServletRequest request,
            @RequestParam String username) {

        HttpSession session = request.getSession(true);

        // 보안 정보 저장
        session.setAttribute("username", username);
        session.setAttribute("userAgent", request.getHeader("User-Agent"));
        session.setAttribute("ipAddress", request.getRemoteAddr());
        session.setAttribute("loginTime", System.currentTimeMillis());
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);

        log.info("Secure session created: sessionId={}, username={}, ip={}",
                session.getId(), username, request.getRemoteAddr());

        return Map.of(
                "sessionId", session.getId(),
                "username", username,
                "userAgent", request.getHeader("User-Agent"),
                "ipAddress", request.getRemoteAddr(),
                "message", "보안 세션 생성 완료"
        );
    }

    /**
     * 5. 모든 디바이스에서 로그아웃 (비밀번호 변경 시)
     */
    @PostMapping("/logout-all-devices")
    public Map<String, Object> logoutAllDevices(@RequestParam String username) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(username);

        int loggedOutCount = 0;
        List<SessionInfo> loggedOutSessions = new ArrayList<>();

        for (Map.Entry<String, ? extends Session> entry : sessions.entrySet()) {
            String sessionId = entry.getKey();
            Session session = entry.getValue();

            // 세션 정보 수집
            SessionInfo info = new SessionInfo();
            info.setSessionId(sessionId);
            info.setLastAccessedTime(session.getLastAccessedTime().toString());
            loggedOutSessions.add(info);

            // 세션 삭제
            sessionRepository.deleteById(sessionId);
            loggedOutCount++;

            log.info("Session deleted: sessionId={}, username={}", sessionId, username);
        }

        return Map.of(
                "username", username,
                "loggedOutCount", loggedOutCount,
                "loggedOutSessions", loggedOutSessions,
                "message", "모든 디바이스에서 로그아웃되었습니다."
        );
    }

    @Data
    public static class SessionInfo {
        private String sessionId;
        private String lastAccessedTime;
    }
}
