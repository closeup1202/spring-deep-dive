package com.exam.springdeepdive.transaction.antipattern;

import com.exam.springdeepdive.transaction.Member;
import com.exam.springdeepdive.transaction.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ReadOnly 트랜잭션 테스트를 위한 보조 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WriteHelperService {

    private final MemberRepository memberRepository;

    /**
     * ⚠️ WARNING: REQUIRED 전파 (기본값)
     *
     * readOnly=true 트랜잭션에서 호출되면 readOnly 상태를 상속받음
     */
    @Transactional  // propagation = Propagation.REQUIRED (기본값)
    public void writeWithRequired() {
        log.info("writeWithRequired() - REQUIRED propagation");
        log.warn("부모가 readOnly면 이 메서드도 readOnly로 실행됨");

        Member member = new Member();
        member.setUsername("Required-Write");
        memberRepository.save(member);  // readOnly 트랜잭션이면 실패 가능
    }

    /**
     * ✅ GOOD: REQUIRES_NEW 전파
     *
     * readOnly 트랜잭션과 독립적인 새로운 쓰기 가능한 트랜잭션 생성
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeWithRequiresNew() {
        log.info("writeWithRequiresNew() - REQUIRES_NEW propagation");
        log.info("부모가 readOnly여도 새로운 쓰기 가능한 트랜잭션 생성");

        Member member = new Member();
        member.setUsername("RequiresNew-Write");
        memberRepository.save(member);  // 성공!

        log.info("Member saved successfully: {}", member.getUsername());
    }
}
