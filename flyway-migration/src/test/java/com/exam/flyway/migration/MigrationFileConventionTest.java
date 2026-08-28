package com.exam.flyway.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * DB 없이 도는 순수 단위 테스트.
 *
 * <p>실무에서 Flyway 사고의 절반은 파일 이름에서 난다.
 * (버전 중복, 언더스코어 한 개, 대소문자 혼용, 오타 난 콜백 이름 → 조용히 무시됨)
 * CI 에서 이 테스트가 그런 실수를 배포 전에 잡는다.
 *
 * <p>경로는 모듈 디렉토리 기준 상대 경로다.
 * Gradle 은 test 태스크의 작업 디렉토리를 모듈 디렉토리로 잡는다.
 */
@DisplayName("마이그레이션 파일 이름 규칙")
class MigrationFileConventionTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path CALLBACK_DIR = Path.of("src/main/resources/db/callback");

    /** V1__설명.sql / V1_1__설명.sql / R__설명.sql */
    private static final Pattern VERSIONED = Pattern.compile("^V(\\d+(?:[._]\\d+)*)__([a-z0-9_]+)\\.sql$");
    private static final Pattern REPEATABLE = Pattern.compile("^R__([a-z0-9_]+)\\.sql$");

    private static final Set<String> CALLBACK_EVENTS = Set.of(
            "beforeMigrate", "beforeRepeatables", "beforeEachMigrate", "afterEachMigrate",
            "afterEachMigrateError", "afterMigrate", "afterMigrateApplied", "afterMigrateError",
            "beforeClean", "afterClean", "afterCleanError",
            "beforeValidate", "afterValidate", "afterValidateError",
            "beforeBaseline", "afterBaseline", "afterBaselineError",
            "beforeRepair", "afterRepair", "afterRepairError",
            "beforeInfo", "afterInfo", "afterInfoError");

    private static List<String> fileNamesIn(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    @Test
    @DisplayName("db/migration 의 모든 파일이 V__ 또는 R__ 규칙을 따른다")
    void migrationFileNamesFollowConvention() throws IOException {
        List<String> invalid = fileNamesIn(MIGRATION_DIR).stream()
                .filter(name -> !VERSIONED.matcher(name).matches())
                .filter(name -> !REPEATABLE.matcher(name).matches())
                .toList();

        assertThat(invalid)
                .as("V1__snake_case.sql 또는 R__snake_case.sql 형식이어야 한다 (언더스코어 2개 주의)")
                .isEmpty();
    }

    @Test
    @DisplayName("버전 번호가 중복되지 않는다")
    void versionsAreUnique() throws IOException {
        Set<String> seen = new HashSet<>();
        List<String> duplicated = new ArrayList<>();

        for (String name : fileNamesIn(MIGRATION_DIR)) {
            Matcher matcher = VERSIONED.matcher(name);
            if (matcher.matches() && !seen.add(matcher.group(1).replace('_', '.'))) {
                duplicated.add(name);
            }
        }

        assertThat(duplicated)
                .as("같은 버전 번호가 둘 이상이면 Flyway 가 부팅 시점에 예외를 던진다")
                .isEmpty();
    }

    @Test
    @DisplayName("콜백 파일 이름이 실제로 존재하는 이벤트를 가리킨다")
    void callbackNamesAreValidEvents() throws IOException {
        List<String> unknown = fileNamesIn(CALLBACK_DIR).stream()
                .map(name -> name.split("__")[0].replace(".sql", ""))
                .filter(event -> !CALLBACK_EVENTS.contains(event))
                .toList();

        assertThat(unknown)
                .as("오타 난 콜백 이름은 에러 없이 그냥 무시되므로 여기서 잡아야 한다")
                .isEmpty();
    }
}
