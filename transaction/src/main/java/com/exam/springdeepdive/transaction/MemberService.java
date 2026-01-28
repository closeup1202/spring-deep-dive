package com.exam.springdeepdive.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void saveAndThrowUncheckedException(String name) {
        memberRepository.save(new Member(name));
        throw new RuntimeException("Unchecked Exception -> Rollback Expected");
    }

    @Transactional
    public void saveAndThrowCheckedException(String name) throws Exception {
        memberRepository.save(new Member(name));
        throw new Exception("Checked Exception -> Commit Expected");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveAndThrowCheckedExceptionWithRollbackOption(String name) throws Exception {
        memberRepository.save(new Member(name));
        throw new Exception("Checked Exception with rollbackFor -> Rollback Expected");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveWithRequiresNew(String name) {
        memberRepository.save(new Member(name));
    }
}
