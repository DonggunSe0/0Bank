# Log Rotation 도입 이유 및 설정 소개

## 1. 도입 이유

서버 로그를 파일로 남기면 장애 분석과 운영 이력 확인에 도움이 된다.
하지만 로그 파일을 계속 하나의 파일에만 쌓으면 다음 문제가 생길 수 있다.

- 로그 파일 크기가 계속 커진다.
- 디스크 용량이 부족해질 수 있다.
- 오래된 로그와 최신 로그가 한 파일에 섞여 확인이 어렵다.
- 파일이 너무 커져서 열람, 검색, 다운로드가 불편해진다.
- 운영 중 불필요한 오래된 로그가 계속 남는다.

그래서 Log Rotation을 적용해 로그 파일을 날짜와 크기 기준으로 나누고, 오래된 로그는 자동으로 정리한다.

## 2. Log Rotation 적용 효과

| 효과 | 설명 |
| --- | --- |
| 파일 크기 제한 | 로그 파일 하나가 너무 커지는 것을 방지한다. |
| 날짜별 관리 | 날짜 단위로 로그를 분리해 장애 발생 시점을 찾기 쉽다. |
| 자동 압축 | 지난 로그를 `.gz`로 압축해 저장 공간을 줄인다. |
| 자동 삭제 | 보관 기간이 지난 로그를 자동으로 삭제한다. |
| 전체 용량 제한 | 로그 전체 용량이 일정 크기를 넘으면 오래된 로그부터 삭제한다. |

## 3. 설정 파일 위치

```text
backend/src/main/resources/logback-spring.xml
```

## 4. 현재 도입된 로그 기능

### 4.1 로그 출력 형식

```xml
<property name="LOG_PATTERN"
          value="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n"/>
```

로그는 다음 정보를 포함한다.

```text
시간 / 로그 레벨 / 실행 스레드 / 로거 이름 / 메시지
```

예시:

```text
2026-06-29 10:15:30.123 INFO  [main] c.t.backend.Application - Started Application
```

### 4.2 콘솔 로그 출력

```xml
<appender name="CONSOLE"
          class="ch.qos.logback.core.ConsoleAppender">
```

콘솔에 로그를 출력한다.

Docker 환경에서는 이 로그를 다음 명령으로 확인할 수 있다.

```bash
docker logs <container-name>
```

### 4.3 파일 로그 저장

```xml
<appender name="FILE"
          class="ch.qos.logback.core.rolling.RollingFileAppender">
```

로그를 파일로도 저장한다.

현재 기록 중인 로그 파일은 다음 경로다.

```xml
<file>logs/app.log</file>
```

애플리케이션 기준 상대 경로이므로 Docker 컨테이너에서는 보통 다음 위치에 저장된다.

```text
/app/logs/app.log
```

### 4.4 날짜 + 크기 기준 로그 분리

```xml
<rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
```

현재 설정은 두 가지 기준으로 로그 파일을 분리한다.

1. 하루가 지나면 새 로그 파일 생성
2. 하루 안에서도 파일 하나가 100MB를 넘으면 추가 분리

```xml
<maxFileSize>100MB</maxFileSize>
```

### 4.5 로그 파일 이름 규칙

```xml
<fileNamePattern>
    logs/app-%d{yyyy-MM-dd}.%i.log.gz
</fileNamePattern>
```

파일명 예시:

```text
app-2026-06-29.0.log.gz
app-2026-06-29.1.log.gz
app-2026-06-30.0.log.gz
```

의미:

| 부분 | 설명 |
| --- | --- |
| `yyyy-MM-dd` | 로그 날짜 |
| `%i` | 같은 날짜 안에서 파일 분할 번호 |
| `.gz` | 압축 저장 |

### 4.6 로그 보관 기간

```xml
<maxHistory>30</maxHistory>
```

30일이 지난 로그 파일은 자동 삭제된다.

### 4.7 전체 로그 용량 제한

```xml
<totalSizeCap>5GB</totalSizeCap>
```

전체 로그 파일 용량이 5GB를 넘으면 오래된 로그부터 삭제된다.

### 4.8 로그 레벨

```xml
<root level="INFO">
```

현재는 `INFO` 이상 로그만 출력한다.

| 로그 레벨 | 출력 여부 |
| --- | --- |
| `TRACE` | 제외 |
| `DEBUG` | 제외 |
| `INFO` | 출력 |
| `WARN` | 출력 |
| `ERROR` | 출력 |

### 4.9 콘솔 + 파일 동시 출력

```xml
<root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
</root>
```

로그는 두 곳에 동시에 남는다.

```text
1. 콘솔
2. 파일
```

## 5. 현재 로그 정책 요약

```text
- 현재 로그 파일: logs/app.log
- 날짜별 로그 분리
- 파일 하나가 100MB를 넘으면 추가 분리
- 지난 로그는 .gz로 압축 저장
- 30일 지난 로그 자동 삭제
- 전체 로그 용량 5GB 초과 시 오래된 로그부터 삭제
- INFO 이상 로그만 기록
- 콘솔과 파일에 동시에 출력
```

## 6. Docker 배포 시 주의 사항

현재 Dockerfile의 실행 위치는 다음과 같다.

```dockerfile
WORKDIR /app
```

따라서 `logs/app.log`는 컨테이너 내부 기준으로 다음 위치에 저장된다.

```text
/app/logs/app.log
```

문제는 컨테이너를 삭제하거나 재배포하면 컨테이너 내부 파일도 함께 사라질 수 있다는 점이다.

Log Rotation 설정만으로는 로그 파일을 EC2 호스트에 안전하게 보관할 수 없다.

운영에서 파일 로그를 유지하려면 배포 스크립트의 `docker run` 옵션에 볼륨 마운트를 추가해야 한다.

예시:

```bash
-v /dockerProjects/app-data/logs:/app/logs
```

의미:

```text
EC2 호스트:        /dockerProjects/app-data/logs
Docker 컨테이너:   /app/logs
```

이렇게 설정하면 컨테이너가 재배포되거나 삭제되어도 로그 파일은 EC2 서버에 남는다.

## 7. 팀원 공유용 한 줄 요약

`logback-spring.xml`을 통해 서버 로그를 콘솔과 파일에 동시에 남기고, 날짜/용량 기준으로 로그 파일을 자동 분리·압축·삭제하도록 Log Rotation을 적용했다. 단, Docker 배포 환경에서는 파일 로그 보존을 위해 EC2 호스트 디렉토리와 `/app/logs`를 볼륨 마운트해야 한다.
