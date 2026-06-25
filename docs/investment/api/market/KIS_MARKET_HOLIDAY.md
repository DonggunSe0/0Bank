# KIS 국내 휴장일 조회 API

## 기본 정보

| 항목          | 값                                                |
|-------------|--------------------------------------------------|
| API 명       | 국내휴장일조회                                          |
| API ID      | 국내주식-040                                         |
| 통신 방식       | REST                                             |
| HTTP Method | GET                                              |
| 실전 TR_ID    | CTCA0903R                                        |
| URL         | `/uapi/domestic-stock/v1/quotations/chk-holiday` |
| 실전 Domain   | `https://openapi.koreainvestment.com:9443`       |
| 모의투자        | 미지원                                              |

---

## 개요

국내 증시의 영업일, 거래일, 개장일, 결제일 여부를 조회한다.

> KIS 공식 권고사항
>
> 해당 API는 원장 서비스와 연관되어 있으므로 서비스 부하 방지를 위해 **1일 1회 호출**을 권장한다.

### 활용 목적

투자 도메인에서는 다음 용도로 사용한다.

- 국내 증시 휴장일 동기화
- 거래 가능 여부 검증
- 정규장 주문 가능 여부 검증
- 휴장일 DB 캐싱

### 중요

주문 가능 여부 판단 시에는 반드시 다음 필드를 사용한다.

```text
opnd_yn
```

| 값 | 의미  |
|---|-----|
| Y | 개장일 |
| N | 휴장일 |

---

# Request

## Header

| Header        | 필수 | 설명                              |
|---------------|----|---------------------------------|
| authorization | O  | Bearer AccessToken              |
| appkey        | O  | KIS AppKey                      |
| appsecret     | O  | KIS AppSecret                   |
| tr_id         | O  | CTCA0903R                       |
| custtype      | O  | P                               |
| content-type  | O  | application/json; charset=utf-8 |

---

## Query Parameter

| Parameter   | 필수 | 설명              |
|-------------|----|-----------------|
| BASS_DT     | O  | 기준일자 (YYYYMMDD) |
| CTX_AREA_NK | O  | 공백              |
| CTX_AREA_FK | O  | 공백              |

---

## Request Example

```http
GET /uapi/domestic-stock/v1/quotations/chk-holiday?BASS_DT=20260101&CTX_AREA_NK=&CTX_AREA_FK=
```

### Header

```http
Authorization: Bearer {accessToken}
appkey: {appKey}
appsecret: {appSecret}
tr_id: CTCA0903R
custtype: P
content-type: application/json; charset=utf-8
```

---

# Response

## 공통 응답

| 필드     | 설명        |
|--------|-----------|
| rt_cd  | 성공 여부     |
| msg_cd | 응답 코드     |
| msg1   | 응답 메시지    |
| output | 휴장일 정보 목록 |

---

## output 필드

| 필드           | 설명     |
|--------------|--------|
| bass_dt      | 기준일    |
| wday_dvsn_cd | 요일 코드  |
| bzdy_yn      | 영업일 여부 |
| tr_day_yn    | 거래일 여부 |
| opnd_yn      | 개장일 여부 |
| sttl_day_yn  | 결제일 여부 |

---

## 요일 코드

| 코드 | 요일  |
|----|-----|
| 01 | 일요일 |
| 02 | 월요일 |
| 03 | 화요일 |
| 04 | 수요일 |
| 05 | 목요일 |
| 06 | 금요일 |
| 07 | 토요일 |

---

## Response Example

```json
{
  "rt_cd": "0",
  "msg_cd": "KIOK0500",
  "msg1": "조회가 계속됩니다.",
  "output": [
    {
      "bass_dt": "20260101",
      "wday_dvsn_cd": "05",
      "bzdy_yn": "N",
      "tr_day_yn": "Y",
      "opnd_yn": "N",
      "sttl_day_yn": "N"
    }
  ]
}
```

---

# 투자 도메인 활용 정책

## 동기화 주기

```text
애플리케이션 시작 시 1회
+
매일 1회 스케줄링
```

---

## 저장 전략

```text
KIS API 조회
↓
휴장일 데이터 추출
↓
시장별 기존 휴장일 삭제
↓
신규 휴장일 저장
↓
메모리 캐시 적재
```

현재 구현은 Upsert를 사용하지 않는다.

휴장일 데이터 수가 적고 임시공휴일 등으로 상태가 변경될 수 있으므로 `deleteByMarketType(MarketType.KRX)` 후 최신 조회 결과를 저장한다.

---

## 거래 검증

주문 요청 시

```text
오늘 날짜 조회
↓
휴장일 여부 확인
↓
opnd_yn = Y
↓
거래 허용
```

```text
opnd_yn = N
↓
주문 거부
```

---

## DB 저장 대상

현재 프로젝트에서는

```text
opnd_yn = N
```

인 데이터만 휴장일 테이블에 저장한다.

예시

- 신정
- 설날
- 추석
- 어린이날
- 임시공휴일

---

## 참고사항

KIS 문서 기준

```text
주문 가능 여부 판단
=
opnd_yn 사용
```

따라서

```text
bzdy_yn
tr_day_yn
```

보다

```text
opnd_yn
```

을 우선적으로 사용한다.
