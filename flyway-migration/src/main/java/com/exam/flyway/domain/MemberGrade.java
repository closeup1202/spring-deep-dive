package com.exam.flyway.domain;

/**
 * 등급 산정 정책. 이 정책이 애플리케이션 코드에 있다는 점이
 * "왜 Java 마이그레이션이 필요한가"의 근거가 된다.
 *
 * @see com.exam.flyway.migration.lab.V20__BackfillMemberGrade
 */
public enum MemberGrade {

    BRONZE(0),
    SILVER(1_000),
    GOLD(10_000),
    PLATINUM(50_000);

    private final int minPoint;

    MemberGrade(int minPoint) {
        this.minPoint = minPoint;
    }

    public static MemberGrade of(int point) {
        MemberGrade result = BRONZE;
        for (MemberGrade grade : values()) {
            if (point >= grade.minPoint) {
                result = grade;
            }
        }
        return result;
    }
}
