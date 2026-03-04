package com.exam.txtemplate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 감사(Audit) 로그.
 * 메인 트랜잭션 성공/실패 여부와 무관하게 반드시 기록해야 하는 이벤트용.
 * REQUIRES_NEW 전파 속성을 가진 별도 TransactionTemplate으로 저장한다.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String action;
    private String detail;
    private String result; // ATTEMPTED, COMMITTED, ROLLED_BACK
    private LocalDateTime createdAt;

    public static AuditLog of(String action, String detail, String result) {
        AuditLog log = new AuditLog();
        log.action    = action;
        log.detail    = detail;
        log.result    = result;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
