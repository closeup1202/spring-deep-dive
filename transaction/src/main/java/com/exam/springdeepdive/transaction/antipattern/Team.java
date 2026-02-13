package com.exam.springdeepdive.transaction.antipattern;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * N+1 Query 테스트를 위한 Team 엔티티
 */
@Entity
@Getter
@Setter
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /**
     * OneToMany는 기본적으로 LAZY 로딩
     * 반복문에서 접근 시 N+1 쿼리 발생
     */
    @OneToMany(mappedBy = "team", fetch = FetchType.LAZY)
    private List<TeamMember> members = new ArrayList<>();

    public Team() {
    }

    public Team(String name) {
        this.name = name;
    }
}
