package com.exam.flyway.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 대응하는 마이그레이션:
 * <pre>
 *   V2__create_member.sql             id, team_id, name, created_at
 *   V3__add_member_email.sql          email
 *   V4__add_member_point_and_grade    point, grade
 * </pre>
 *
 * 여기에 필드를 추가하면 반드시 새 마이그레이션도 함께 써야 한다.
 * 마이그레이션 없이 필드만 추가하면 ddl-auto=validate 가 부팅 시점에 막아준다. (STEPS 3단계)
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "point", nullable = false)
    private int point;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", length = 20)
    private MemberGrade grade;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Member(Team team, String name, String email, int point) {
        this.team = team;
        this.name = name;
        this.email = email;
        this.point = point;
    }
}
