## Branch Naming Convention

- `main`: 배포 브랜치
- `develop`: 개발 통합 브랜치

### 작업 브랜치

- `feature/{기능명}`: 기능 개발
- `fix/{수정내용}`: 버그 수정
- `hotfix/{수정내용}`: 배포 환경 긴급 수정
- `refactor/{대상}`: 기능 변경 없는 코드 개선
- `docs/{문서명}`: 문서 수정
- `chore/{작업명}`: 설정, 빌드, 환경 구성 작업
- `test/*`: 테스트 코드 추가 또는 수정

### 예시

- `feature/auth`
- `feature/transfer`
- `fix/transfer-validation`
- `hotfix/deploy-error`
- `refactor/account-service`
- `docs/readme`
- `chore/github-actions`
- `test/transfer`

## Flyway Migration Convention

- 마이그레이션 파일 위치는 `backend/src/main/resources/db/migration`을 사용한다.
- 파일명은 `V{버전}__{설명}.sql` 형식을 지킨다. 예: `V2__add_user_last_login_at.sql`
- 버전과 설명 사이에는 언더스코어 2개(`__`)를 사용한다.
- 한 번 `main` 또는 `develop`에 반영된 마이그레이션 파일은 수정하지 않는다.
- 이미 반영된 스키마를 변경해야 하면 기존 파일을 고치지 말고 새 버전 파일을 추가한다.
- dev/prod 환경에서는 Flyway가 스키마 생성과 변경을 담당하고, Hibernate는 `ddl-auto: validate`만 사용한다.
- 운영 DB에 직접 DDL을 수동 적용하지 않는다. 필요한 변경은 반드시 Flyway SQL로 남긴다.
- `flyway_schema_history` 테이블은 Flyway 관리 테이블이므로 직접 수정하거나 삭제하지 않는다.
- nullable 변경, unique/index 추가, FK 변경, 금액 컬럼 precision/scale 변경은 PR에서 반드시 리뷰한다.
- 기존 데이터가 있는 테이블에 `NOT NULL` 컬럼을 추가할 때는 `NULL 허용 추가 -> 데이터 보정 -> NOT NULL 변경` 순서로 작성한다.
