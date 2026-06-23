# 휴장일(MarketHoliday) 동기화 설계

## 목표

KIS 국내휴장일조회 API를 통해 국내 증시 휴장일 정보를 주기적으로 동기화하고, 주문 가능 여부 검증에 활용한다.

---

## 최종 설계 방향

### 1. 저장 대상

KIS 응답 데이터 전체를 저장하지 않는다.

현재 프로젝트에서는 주문 가능 여부 검증만 필요하므로 다음 조건을 만족하는 날짜만 저장한다.

text opnd_yn = N

즉, 실제 휴장일만 market_holidays 테이블에 저장한다.

---

### 2. MarketHoliday 엔티티

현재 구조를 유지한다.

```md
MarketHoliday
----------------------
marketType
holidayDate
```

isOpen 컬럼은 추가하지 않는다.

---

### 3. 최초 동기화

애플리케이션 시작 시 1회 수행한다.

절차

```md
Application Start
↓
DB 기존 휴장일 캐시 적재
↓
KIS 휴장일 API 조회
↓
휴장일(opnd_yn = N) 추출
↓
market_holidays 저장
↓
메모리 캐시 적재
```

---

### 4. KIS API 조회 범위

KIS API는 기준일로부터 약 23일 정도의 데이터만 반환한다.

---

### 5. 동기화 범위

최초 실행 , 주기적 동기화 모두 API 1회 호출에 대한 결과만 사용하여 동기화한다

---

### 6. 주기적 동기화

스케줄러를 통해 매일 새벽 실행한다.

예시

java @Scheduled(cron = "0 0 4 * * *")

---

### 7. 동기화 방식

Upsert 사용하지 않는다.

이유

- 휴장일 데이터 수가 매우 적음
- 임시공휴일 지정 등으로 휴장일 상태가 변경될 수 있음
- 전체 재생성이 가장 단순하고 안전함

동기화 절차

```md
API 조회
↓
휴장일 목록 생성
↓
기존 휴장일 전체 삭제
↓
신규 휴장일 전체 저장
↓
캐시 교체
```

---

### 8. 삭제 범위

전체 삭제 대신 시장별 삭제를 사용한다.

java deleteByMarketType(MarketType.KRX)

향후 해외 시장(NASDAQ, NYSE 등) 확장을 고려한다.

---

### 9. 메모리 캐시

휴장일 검증 성능 향상을 위해 메모리 캐시를 사용한다.

예시

java Set<LocalDate> holidayCache

---

### 10. 캐시 갱신 방식

캐시는 수정하지 않고 교체 방식으로 갱신한다.

java holidayCache = newHolidaySet;

절차

text DB 저장 완료 -> 새 Set 생성 -> 캐시 교체

---

### 11. 주문 가능 여부 검증

DB를 직접 조회하지 않는다.

캐시를 우선 사용한다.

예시

java public boolean isOpenDate(LocalDate date) { return !holidayCache.contains(date); }

---

### 12. DB의 역할

DB는 조회용이 아닌 영속 저장소 역할을 수행한다.

목적

- 서버 재시작 시 데이터 복구
- KIS 장애 시 기존 데이터 활용
- 캐시 초기화 시 재적재 가능

실제 주문 검증은 캐시 기반으로 수행한다.

---

## 구현 대상

### 패키지

현재 코드 기준 휴장일 도메인은 `investment/marketholiday` 하위에서 관리한다.

외부 KIS 휴장일 API 호출 코드는 `investment/client/marketholiday` 하위에서 관리한다.

### 신규 컴포넌트

- KisHolidayClient
- MarketHolidaySyncService
- MarketHolidayCache

### 스케줄링

- 앱 시작 시 최초 동기화
- 매일 새벽 정기 동기화

### Repository

java void deleteByMarketType(MarketType marketType);

추가
