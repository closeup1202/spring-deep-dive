package com.exam.flyway.migration.lab;

import com.exam.flyway.domain.MemberGrade;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * ── STEPS 7단계 실습 파일 (아직 실행되지 않음) ──────────────────────────────
 *
 * <p>Flyway 는 {@code spring.flyway.locations} 에 지정된 경로만 스캔한다.
 * 이 클래스는 {@code com.exam.flyway.migration.lab} 패키지에 있고
 * 이 경로는 locations 에 없으므로 컴파일만 되고 실행되지는 않는다.
 *
 * <p>실행시키려면 <b>application-local.yml</b> 의 locations 에 아래를 추가한다:
 * <pre>classpath:com/exam/flyway/migration/lab</pre>
 * Java 마이그레이션의 location 은 파일 경로가 아니라 <b>패키지 경로</b>다.
 *
 * <p><b>왜 SQL 이 아니라 Java 인가</b><br>
 * 등급 산정 규칙({@link MemberGrade})이 애플리케이션 코드에 있다.
 * SQL 로 CASE WHEN 을 다시 짜면 정책이 두 곳에 중복되고 언젠가 어긋난다.
 * 암호화/해싱, 외부 API 호출, 복잡한 파싱처럼 SQL 로 표현하기 어려운 백필도 같은 이유로 Java 를 쓴다.
 *
 * <p><b>주의</b><br>
 * Java 마이그레이션은 Spring 빈이 아니다. DI 를 받을 수 없고,
 * {@link Context#getConnection()} 으로 받은 커넥션을 그대로 써야 한다.
 * (커넥션을 직접 close 하면 안 된다 — Flyway 가 관리한다.)
 */
public class V20__BackfillMemberGrade extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        Map<Long, Integer> points = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "select id, point from member where grade is null")) {
            while (rs.next()) {
                points.put(rs.getLong("id"), rs.getInt("point"));
            }
        }

        try (PreparedStatement update = connection.prepareStatement(
                "update member set grade = ? where id = ?")) {
            for (Map.Entry<Long, Integer> entry : points.entrySet()) {
                update.setString(1, MemberGrade.of(entry.getValue()).name());
                update.setLong(2, entry.getKey());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    /**
     * SQL 마이그레이션은 파일 내용으로 checksum 을 계산하지만,
     * Java 마이그레이션은 기본적으로 checksum 이 null 이다.
     * → 클래스 내용을 고쳐도 Flyway 가 알아채지 못한다.
     * 로직 변경을 감지하고 싶으면 이렇게 직접 값을 올려준다.
     */
    @Override
    public Integer getChecksum() {
        return 1;
    }
}
