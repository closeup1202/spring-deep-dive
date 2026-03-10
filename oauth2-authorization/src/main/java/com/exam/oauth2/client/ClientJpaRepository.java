package com.exam.oauth2.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 클라이언트 JPA 레포지토리
 *
 * Spring Data JPA가 구현체를 자동 생성.
 * CustomRegisteredClientRepository에서 의존.
 */
@Repository
public interface ClientJpaRepository extends JpaRepository<ClientEntity, String> {

    /**
     * clientId로 클라이언트 조회
     * RegisteredClientRepository.findByClientId()에서 사용
     */
    Optional<ClientEntity> findByClientId(String clientId);
}
