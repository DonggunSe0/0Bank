# Flyway 도입 과정 정리

## 도입 목적

운영 환경에서는 Hibernate의 `ddl-auto: update/create`로 DB 스키마를 자동 변경하지 않고, SQL 마이그레이션 파일로 변경 이력을 관리하기 위해 Flyway를 도입했다.

이번 도입에서는 과거 MVP 개발 중 발생한 모든 변경 이력을 복원하지 않고, 현재 엔티티 기준의 스키마를 `V1__baseline_schema.sql`로 잡은 뒤 이후 변경분부터 `V2`, `V3` 순서로 관리한다.

## 최종 구조

```text
backend/src/main/resources/db/migration/
└── V1__baseline_schema.sql
```

`db/migration`은 Spring Boot + Flyway의 기본 마이그레이션 경로다.

```yaml
spring:
  flyway:
    locations: classpath:db/migration
```

별도 설정을 하지 않아도 기본적으로 `classpath:db/migration`을 스캔하지만, 명시해두면 팀원이 설정 의도를 바로 이해할 수 있다.

## 1. Flyway 의존성 추가

`backend/build.gradle.kts`에 Flyway 의존성을 추가했다.

```kotlin
dependencies {
    // Database
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-mysql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")
}
```

MySQL을 사용하므로 `flyway-mysql`도 함께 추가했다.

## 2. baseline DB 생성

현재 엔티티 기준의 초기 스키마 SQL을 만들기 위해 임시 DB를 생성했다.

처음에는 로컬에 `mysql` 명령어가 없어 아래 명령이 실패했다.

```bash
mysql -u root -p -e "CREATE DATABASE team10_baseline CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

프로젝트는 Docker Compose로 MySQL을 사용하므로, Docker MySQL 컨테이너의 `mysql` 명령어를 사용했다.

```bash
cd /Users/mac/study/programmers_study/NBE9-11-4-Team10/backend
docker compose up -d mysql
```

```bash
docker exec mysql mysql -u root -p1234 -e "CREATE DATABASE team10_baseline CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

## 3. Hibernate로 현재 엔티티 기준 테이블 생성

Flyway의 V1 파일을 직접 손으로 작성하면 누락이나 오타 위험이 크기 때문에, 먼저 Hibernate가 현재 엔티티 기준으로 임시 DB에 테이블을 만들게 했다.

이를 위해 임시 프로필 파일 `application-baseline.yml`을 만들었다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/team10_baseline
    username: root
    password: 1234
    driver-class-name: com.mysql.cj.jdbc.Driver

  flyway:
    enabled: false

  jpa:
    hibernate:
      ddl-auto: create
    show-sql: true

codef:
  client-id: dummy
  client-secret: dummy
```

핵심 설정은 다음과 같다.

```yaml
spring:
  flyway:
    enabled: false

  jpa:
    hibernate:
      ddl-auto: create
```

이 단계에서는 Flyway를 끄고, Hibernate가 엔티티를 보고 테이블을 생성하게 한다.

앱 실행:

```bash
./gradlew bootRun --args='--spring.profiles.active=baseline'
```

실행 중 `Table 'team10_baseline.xxx' doesn't exist` 경고가 여러 개 나올 수 있다. `ddl-auto: create`가 기존 FK/테이블을 먼저 drop하려고 할 때 빈 DB라서 발생하는 경고이며, 이후 `create table ...` 로그가 출력되면 테이블 생성은 진행된 것이다.

앱 실행 마지막에 `codef.client-id` 같은 외부 API 설정 누락으로 실패할 수 있다. 이번 단계의 목적은 DB 테이블 생성이므로, 테이블 생성 로그가 출력되었다면 dump 단계로 넘어갈 수 있다. 필요하면 baseline 프로필에 dummy 값을 넣어 앱 컨텍스트 생성을 통과시킨다.

## 4. 현재 스키마를 V1 SQL로 dump

마이그레이션 디렉터리를 생성한다.

```bash
mkdir -p src/main/resources/db/migration
```

Docker MySQL 컨테이너에서 `mysqldump`를 실행해 schema-only dump를 생성한다.

```bash
docker exec mysql mysqldump -u root -p1234 \
  --no-data \
  --skip-comments \
  team10_baseline > src/main/resources/db/migration/V1__baseline_schema.sql
```

옵션 의미:

- `--no-data`: 실제 데이터는 제외하고 테이블 구조만 dump한다.
- `--skip-comments`: dump 생성 시각 등 불필요한 주석을 줄인다.
- `team10_baseline`: Hibernate가 테이블을 생성한 임시 DB다.

생성된 파일:

```text
backend/src/main/resources/db/migration/V1__baseline_schema.sql
```

## 5. V1 SQL 내용 확인

다음 내용을 확인했다.

- `CREATE TABLE` 28개 포함
- `INSERT INTO` 없음
- `CREATE DATABASE` 없음
- `USE team10_baseline` 없음
- `flyway_schema_history` 없음
- 특정 DB명에 고정된 SQL 없음

확인 명령:

```bash
grep -nE "CREATE TABLE|ALTER TABLE|CREATE DATABASE|USE |INSERT INTO|flyway_schema_history|team10_baseline" \
  src/main/resources/db/migration/V1__baseline_schema.sql
```

`mysqldump`로 생성한 원본에는 `DROP TABLE IF EXISTS`가 포함될 수 있다. 이 구문은 기존 데이터가 있는 DB에서 잘못 실행될 경우 데이터 유실 위험이 있으므로, `V1__baseline_schema.sql`에서는 모두 제거했다.

제거 여부 확인:

```bash
grep -n "DROP TABLE IF EXISTS" src/main/resources/db/migration/V1__baseline_schema.sql
```

위 명령에서 결과가 출력되지 않아야 한다. 이미 테이블이 존재하는 DB에서 V1이 실행되면 테이블을 삭제하지 않고 실패하도록 두는 것이 더 안전하다.

## 6. V1 SQL을 빈 DB에 직접 적용해 검증

Flyway가 실행하기 전에 SQL 자체가 MySQL에서 끝까지 실행되는지 확인했다.

검증용 DB 생성:

```bash
docker exec mysql mysql -u root -p1234 -e "DROP DATABASE IF EXISTS team10_flyway_verify; CREATE DATABASE team10_flyway_verify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

V1 SQL 적용:

```bash
docker exec -i mysql mysql -u root -p1234 team10_flyway_verify < src/main/resources/db/migration/V1__baseline_schema.sql
```

테이블 수 확인:

```bash
docker exec mysql mysql -u root -p1234 -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='team10_flyway_verify';"
```

결과:

```text
28
```

## 7. Flyway로 실제 마이그레이션 검증

Flyway가 V1을 직접 실행하고 `flyway_schema_history`를 만드는지 검증했다.

검증용 빈 DB 생성:

```bash
docker exec mysql mysql -u root -p1234 -e "DROP DATABASE IF EXISTS team10_flyway_test; CREATE DATABASE team10_flyway_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

`application-baseline.yml`을 Flyway 검증용으로 변경했다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:13306/team10_flyway_test
    username: root
    password: 1234
    driver-class-name: com.mysql.cj.jdbc.Driver

  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true

codef:
  client-id: dummy
  client-secret: dummy
```

핵심 설정:

```yaml
spring:
  flyway:
    enabled: true

  jpa:
    hibernate:
      ddl-auto: validate
```

이 단계에서는 Flyway가 테이블을 만들고, Hibernate는 엔티티와 DB 스키마가 맞는지 검증만 한다.

앱 실행:

```bash
./gradlew bootRun --args='--spring.profiles.active=baseline'
```

정상 로그:

```text
Creating Schema History table `team10_flyway_test`.`flyway_schema_history`
Migrating schema `team10_flyway_test` to version "1 - baseline schema"
Successfully applied 1 migration to schema `team10_flyway_test`, now at version v1
Started BackendApplication
```

DB 테이블 수 확인:

```bash
docker exec mysql mysql -u root -p1234 -N -e "SELECT table_schema, COUNT(*) FROM information_schema.tables WHERE table_schema = 'team10_flyway_test' GROUP BY table_schema;"
```

정상 결과:

```text
team10_flyway_test    29
```

V1에서 생성한 테이블 28개와 Flyway 관리 테이블 `flyway_schema_history` 1개가 합쳐져 29개가 된다.

Flyway 이력 확인:

```bash
docker exec mysql mysql -u root -p1234 -e "SELECT installed_rank, version, description, type, success FROM team10_flyway_test.flyway_schema_history;"
```

정상 결과 예시:

```text
installed_rank  version  description      type  success
1               1        baseline schema  SQL   1
```

## 8. Redis 경고

검증 중 다음 경고가 발생할 수 있다.

```text
Redis 환율 캐시 저장 실패. DB 동기화는 완료되었습니다.
Unable to connect to Redis
Connection refused: localhost/127.0.0.1:6379
```

이는 Flyway 실패가 아니다. 현재 Docker Compose에서 MySQL만 실행했거나 Redis 서비스가 없어서 환율 캐시 저장만 실패한 것이다. 로그상 DB 동기화는 완료되며, Flyway 마이그레이션 성공 여부와는 별개다.

## 9. 임시 baseline 프로필 삭제

`application-baseline.yml`은 V1 생성/검증용 임시 파일이므로 커밋하지 않는다.

삭제:

```bash
rm src/main/resources/application-baseline.yml
```

최종 커밋 대상:

```text
backend/build.gradle.kts
backend/src/main/resources/db/migration/V1__baseline_schema.sql
```

## 10. dev/prod 적용 방향

아직 prod DB에 데이터가 없으므로 `baseline-on-migrate`는 사용하지 않는다. 빈 DB에서 V1부터 정상 적용되게 한다.

dev와 prod 모두 Flyway를 켜고 Hibernate는 validate로 둔다.

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true

  jpa:
    hibernate:
      ddl-auto: validate
```

test 환경은 기존 테스트 구조 안정성을 위해 우선 `create-drop`을 유지할 수 있다. 이후 운영 유사성을 더 높이고 싶다면 Testcontainers + Flyway 기반으로 전환을 검토한다.

## 핵심 요약

- `V1__baseline_schema.sql`은 현재 엔티티 기준의 초기 스키마다.
- Flyway는 `flyway_schema_history` 테이블을 자동 생성해 적용된 마이그레이션 이력을 기록한다.
- 이미 적용된 V1은 다시 실행되지 않는다.
- 이후 DB 변경은 `V2__...sql`, `V3__...sql`처럼 새 파일로 추가한다.
- 한 번 공유/적용된 마이그레이션 파일은 수정하지 않는다.
