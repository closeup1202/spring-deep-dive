package com.exam.testpractice.service;

import org.springframework.stereotype.Service;

/**
 * 이메일 발송 서비스 (외부 API 호출 가정)
 */
@Service
public class EmailService {

    /**
     * 환영 이메일 발송
     */
    public void sendWelcomeEmail(String email, String name) {
        // 실제로는 외부 API 호출 (SendGrid, AWS SES 등)
        System.out.println("Sending welcome email to: " + email);
    }

    /**
     * 비밀번호 재설정 이메일 발송
     */
    public void sendPasswordResetEmail(String email) {
        // 실제로는 외부 API 호출
        System.out.println("Sending password reset email to: " + email);
    }

    /**
     * 알림 이메일 발송 (여러 명)
     */
    public void sendNotification(String... emails) {
        // 실제로는 외부 API 호출
        for (String email : emails) {
            System.out.println("Sending notification to: " + email);
        }
    }
}
