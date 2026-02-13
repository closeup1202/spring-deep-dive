package com.exam.springdeepdive.transaction.antipattern;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * N+1 Query 테스트를 위한 TeamMember 엔티티
 */
@Entity
@Getter
@Setter
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    public TeamMember() {
    }

    public TeamMember(String name, Team team) {
        this.name = name;
        this.team = team;
    }
}
