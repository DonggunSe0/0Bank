# KOSPI 종목 마스터 동기화

## 목적

한국투자증권(KIS)에서 제공하는 KOSPI 종목 마스터 파일을 다운로드하여 종목 정보를 최신화한다.

현재 프로젝트에서는 KOSPI 종목만 관리한다.

---

# 다운로드 URL

실전 서버 기준

text https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip

동기화 시 다음 순서로 수행한다.

text 1. mst.zip 다운로드 2. 압축 해제 3. kospi_code.mst 파싱 4. Stock 테이블 최신화

---

# 파일 구조

압축 해제 시 다음 파일이 생성된다.

```text
kospi_code.mst
```

해당 파일은 고정 길이(Fixed Width) 형식이다.

CSV가 아니다.

---

# 실제 파일 예시

```text
000120   KR7000120006CJ대한통운                              ST2001900000000 NN9NNY NNNNNNNNN0NNNNNNYY0000798000000100001NNN00NNN000000100N0900000000873770000000050001956070200000000002281200000000011406172000012       0 NYY00003214500000092100000055400379000005.9220260331000018204720NNN
005610   KR7005610001삼립                                    ST3002700050000 NN0NNN NNNNNNNNN0NNNNNNNN0000404000000100001NNN00NNN000000100N0900000000058580000000050001975050200000000000862900000000004314504500012       0 NNY000008123-00000043-00000082-0068-00000.5320260331000003486   NNN
005680   KR7005680004삼영전자                                ST3002700130000 NN0NNN NNNNNNNNN0NNNNNNNN0000131200000100001NNN00NNN000000100N0900000000542630000000005001976122800000000002000000000000001000000000012       0 NNY00000039200000002100000004300035000000.7520260331000002624   NNN
005690   KR7005690003파미셀                                  ST2002700130000 NN0NNY NNNNYNNNN0NNNNNNNN0000138100000100001NNN00NNN000000100N0900000011129040000000005001988052000000000006000400000000003000200700012       0 NNY00000064900000004700000006300063000007.5920241231000008286   NNN
005720   KR7005720008넥센                                    ST3002700080000 NN0NNN NNNNNNNNN0NNNNNNNN0000061000000100001NNN00NNN000000100N0900000000161430000000005001987121900000000005254300000000002677198850012       0 NNY00000937500000067600000094100731000007.7920260331000003205   NNN
```

```text
/*****************************************************************************
 *  코스피 종목 코드 파일 구조
 ****************************************************************************/
typedef struct
{
    char    mksc_shrn_iscd[SZ_SHRNCODE];        /* 단축코드                                     */
    char    stnd_iscd[SZ_STNDCODE];             /* 표준코드                                     */
    char    hts_kor_isnm[SZ_KORNAME];           /* 한글종목명                                   */
    char    scrt_grp_cls_code[2];               /* 증권그룹구분코드                             */
                                                /* ST:주권 MF:증권투자회사 RT:부동산투자회사      */
                                                /* SC:선박투자회사 IF:사회간접자본투융자회사      */
                                                /* DR:주식예탁증서 EW:ELW EF:ETF                */
                                                /* SW:신주인수권증권 SR:신주인수권증서            */
                                                /* BC:수익증권 FE:해외ETF FS:외국주권            */
	char    avls_scal_cls_code[1];              /* 시가총액 규모 구분 코드 유가                   */
												/* (0:제외 1:대 2:중 3:소)                      */
    char    bstp_larg_div_code[4];              /* 지수 업종 대분류 코드                        */
    char    bstp_medm_div_code[4];              /* 지수 업종 중분류 코드                        */
    char    bstp_smal_div_code[4];              /* 지수 업종 소분류 코드                        */
    char    mnin_cls_code_yn[1];                /* 제조업 구분 코드 (Y/N)                       */
    char    low_current_yn[1];               	/* 저유동성종목 여부 							*/
    char    sprn_strr_nmix_issu_yn[1];          /* 지배 구조 지수 종목 여부 (Y/N)                */
    char    kospi200_apnt_cls_code[1];          /* KOSPI200 섹터업종(20110401 변경됨) 			*/
                                                /* 0:해당없음 1:건설 2:중공업 3:철강소재 	        */
												/* 4:에너지화학 5:정보통신 6:금융 7:필수소비재 	*/
												/* 8:자유소비재 9:산업재                        */
												/* A:건강관리 B:커뮤니케이션서비스                */
    char    kospi100_issu_yn[1];                /* KOSPI100여부                                 */
    char    kospi50_issu_yn[1];                 /* KOSPI50 종목 여부                            */
    char    krx_issu_yn[1];                     /* KRX 종목 여부                                */
    char    etp_prod_cls_code[1];            	/* ETP 상품구분코드								*/
												/* 0:해당없음 1:투자회사형 2:수익증권형			*/
												/* 3:ETN 4:손실제한ETN 5:상장형수익증권 			*/
    char    elw_pblc_yn[1];                     /* ELW 발행여부 (Y/N)                           */
    char    krx100_issu_yn[1];                  /* KRX100 종목 여부 (Y/N)                       */
    char    krx_car_yn[1];                      /* KRX 자동차 여부                              */
    char    krx_smcn_yn[1];                     /* KRX 반도체 여부                              */
    char    krx_bio_yn[1];                      /* KRX 바이오 여부                              */
    char    krx_bank_yn[1];                     /* KRX 은행 여부                                */
    char    etpr_undt_objt_co_yn[1];            /* 기업인수목적회사여부 					     	*/
    char    krx_enrg_chms_yn[1];                /* KRX 에너지 화학 여부                         */
    char    krx_stel_yn[1];                     /* KRX 철강 여부                                */
    char    short_over_cls_code[1];             /* 단기과열종목구분코드 0:해당없음              */
                                                /* 1:지정예고 2:지정 3:지정연장(해제연기)       */
    char    krx_medi_cmnc_yn[1];                /* KRX 미디어 통신 여부                         */
    char    krx_cnst_yn[1];                     /* KRX 건설 여부                                */
    char    krx_fnnc_svc_yn[1];                 /* 삭제됨(20151218)								*/
    char    krx_scrt_yn [1];                    /* KRX 증권 구분                                */
    char    krx_ship_yn [1];                    /* KRX 선박 구분                                */
    char    krx_insu_yn[1];                     /* KRX섹터지수 보험여부                         */
    char    krx_trnp_yn[1];                     /* KRX섹터지수 운송여부                         */
	char	sri_nmix_yn[1];                     /* SRI 지수여부 (Y,N)                           */
	char    stck_sdpr[9];                       /* 주식 기준가                                  */
    char    frml_mrkt_deal_qty_unit[5];         /* 정규 시장 매매 수량 단위                     */
    char    ovtm_mrkt_deal_qty_unit[5];         /* 시간외 시장 매매 수량 단위                   */
    char    trht_yn[1];                         /* 거래정지 여부                                */
    char    sltr_yn[1];                         /* 정리매매 여부                                */
    char    mang_issu_yn[1];                    /* 관리 종목 여부                               */
    char    mrkt_alrm_cls_code[2];              /* 시장 경고 구분 코드 (00:해당없음 01:투자주의 */
                                                /* 02:투자경고 03:투자위험                      */
	char    mrkt_alrm_risk_adnt_yn[1];          /* 시장 경고위험 예고 여부                      */
    char    insn_pbnt_yn[1];                    /* 불성실 공시 여부                             */
    char    byps_lstn_yn[1];                    /* 우회 상장 여부                               */
    char    flng_cls_code[2];                   /* 락구분 코드 (00:해당사항없음 01:권리락       */
                                                /* 02:배당락 03:분배락 04:권배락 05:중간배당락  */
                                                /* 06:권리중간배당락 99:기타                    */
                                                /* S?W,SR,EW는 미해당(SPACE)                     */
    char    fcam_mod_cls_code[2];               /* 액면가 변경 구분 코드 (00:해당없음           */
                                                /* 01:액면분할 02:액면병합 99:기타              */
    char    icic_cls_code[2];                   /* 증자 구분 코드 (00:해당없음 01:유상증자      */
                                                /* 02:무상증자 03:유무상증자 99:기타)           */
    char    marg_rate[3];                       /* 증거금 비율                                  */
    char    crdt_able[1];                       /* 신용주문 가능 여부                           */
    char    crdt_days[3];                       /* 신용기간                                     */
    char    prdy_vol[12];                       /* 전일 거래량                                  */
    char    stck_fcam[12];                      /* 주식 액면가                                  */
    char    stck_lstn_date[8];                  /* 주식 상장 일자                               */
    char    lstn_stcn[15];                      /* 상장 주수(천)                                */
    char    cpfn[21];                           /* 자본금                                       */
    char    stac_month[2];                      /* 결산 월                                      */
    char    po_prc[7];                          /* 공모 가격                                    */
    char    prst_cls_code[1];                   /* 우선주 구분 코드 (0:해당없음(보통주)         */
                                                /* 1:구형우선주 2:신형우선주                    */
    char    ssts_hot_yn[1];                     /* 공매도과열종목여부  							*/
    char    stange_runup_yn[1];                 /* 이상급등종목여부 							*/
    char    krx300_issu_yn[1];                  /* KRX300 종목 여부 (Y/N)                       */
    char    kospi_issu_yn[1];                   /* KOSPI여부                                    */
	char	sale_account[9];					/* 매출액                                       */
	char    bsop_prfi[9];						/* 영업이익                                     */
	char	op_prfi[9];							/* 경상이익                                     */
	char	thtr_ntin[5];						/* 당기순이익                                   */
	char	roe[9];								/* ROE(자기자본이익률)                          */
	char	base_date[8];						/* 기준년월                                     */
	char	prdy_avls_scal[9];					/* 전일기준 시가총액 (억)                       */
	char	grp_code[3];						/* 그룹사 코드                                  */
    char    co_crdt_limt_over_yn[1];            /* 회사신용한도초과여부                         */
    char    secu_lend_able_yn[1];               /* 담보대출가능여부                             */
    char    stln_able_yn[1];                    /* 대주가능여부                                 */
}   ST_KSP_CODE;

```

---

# 파싱 방식

KIS 공식 샘플 코드를 기준으로 파싱한다.

파일은 두 영역으로 나뉜다.

## Part 1

행 끝에서 Part 2의 227바이트를 제외한 영역

```text
단축코드 표준코드 한글종목명
```

예시

```text
000120 KR7000120006 CJ대한통운
```

---

## Part 2

행 마지막 227바이트

고정 길이 필드(Fixed Width)

KIS에서 제공하는 field specification을 사용하여 파싱한다.

공식 Python 샘플에서는 `row[-228:]`로 Part 2를 분리한다.

이때 `row`는 파일에서 한 줄을 읽은 문자열이므로 끝의 줄바꿈 문자 `\n`까지 포함한다.

즉 `row[-228:]`은 "Part 2 데이터 227글자 + 줄바꿈 1글자"를 의미한다.

현재 서버 파서는 줄바꿈을 제거한 바이트 라인을 기준으로 처리하므로 Part 2 데이터 폭인 227바이트를 사용한다.

현재 정리한 struct의 `char[n]` 길이를 합산해도 Part 2는 227바이트다.

실제 샘플 라인도 227바이트 기준으로 잘라야 `scrt_grp_cls_code`가 `ST`, `EF`, `EN`, `BC`, `FS` 등으로 시작하고, `trht_yn`, `sltr_yn`, `mang_issu_yn`이 `Y/N` 값으로 정상 매핑된다.

228바이트로 자르면 Part 1의 종목명 padding 공백 1바이트가 Part 2 앞에 포함되어 모든 인덱스가 한 칸씩 밀린다.

주요 저장 필드의 Part 2 offset은 다음과 같다.

| 필드             | 길이 | start | end(exclusive) |
|----------------|----:|------:|---------------:|
| trht_yn        |   1 |    60 |             61 |
| sltr_yn        |   1 |    61 |             62 |
| mang_issu_yn   |   1 |    62 |             63 |
| prdy_vol       |  12 |    81 |             93 |
| stck_lstn_date |   8 |   105 |            113 |
| cpfn           |  21 |   128 |            149 |
| sale_account   |   9 |   163 |            172 |
| thtr_ntin      |   5 |   190 |            195 |
| prdy_avls_scal |   9 |   212 |            221 |

---

# 주요 필드

현재 프로젝트에서 필요한 필드만 저장한다.

## 단축코드

```text
mksc_shrn_iscd
 ```

실제 거래에 사용하는 종목 코드

예)

text 005930 000660 035720

---

## 표준코드(ISIN)

```text
stnd_iscd
```

예)

```text
KR7005930003 
```

---

## 종목명

```text
hts_kor_isnm
```

예)

```text
 삼성전자 SK하이닉스
```

---

## 상장일자

```text
stck_lstn_date
```

---

## 자본금

```text
cpfn
```

---

## 매출액

text sale_account

---

## 당기순이익

text thtr_ntin

---

## 전일 기준 시가총액(억원)

text prdy_avls_scal

---

## 전일 거래량

text prdy_vol

---

## 거래정지 여부

text trht_yn

---

## 관리종목 여부

text mang_issu_yn

---

## 정리매매 여부

text sltr_yn

---

# Stock 엔티티 매핑

| MST 필드         | Stock          |
|----------------|----------------|
| mksc_shrn_iscd | stockCode      |
| stnd_iscd      | standardCode   |
| hts_kor_isnm   | stockName      |
| stck_lstn_date | listedDate     |
| cpfn           | capitalAmount  |
| sale_account   | salesAmount    |
| thtr_ntin      | netIncome      |
| prdy_avls_scal | marketCap      |
| prdy_vol       | previousVolume |
| trht_yn        | status 판단      |
| mang_issu_yn   | status 판단      |
| sltr_yn        | status 판단      |

---

# 종목 상태 판정

현재 프로젝트 enum

java StockStatus

java ACTIVE SUSPENDED DELISTED

다음 기준으로 상태를 결정한다.

## DELISTED

text 정리매매 = Y

java if ("Y".equals(sltrYn)) { return DELISTED; }

---

## SUSPENDED

text 거래정지 = Y 또는 관리종목 = Y

java if ("Y".equals(trhtYn) || "Y".equals(mangIssuYn)) { return SUSPENDED; }

---

## ACTIVE

text 거래정지 = N 관리종목 = N 정리매매 = N

java return ACTIVE;

---

# 동기화 전략

## 기본 원칙

Stock 테이블은 전체 삭제 후 재삽입하지 않는다.

종목은 다음 엔티티에서 참조된다.

text InvestmentOrder InvestmentHolding StockWatchlist

따라서 기존 PK 및 연관관계를 유지해야 한다.

---

## 신규 종목

text DB 없음 → INSERT

---

## 기존 종목

text DB 존재 → UPDATE

업데이트 대상

text 표준코드 종목명 상태 상장일 자본금 매출액 당기순이익 시가총액 전일 거래량

---

## Upsert 기준

text stockCode

를 기준으로 수행한다.

text 파일 파싱 ↓ stockCode 조회 ↓ 존재하면 updateMaster() ↓ 없으면 create()

---

## 파일에 존재하지 않는 종목

최신 MST 파일에 존재하지 않는 종목은 DELISTED로 처리한다.

동기화 기준 시각을 `syncStartedAt`으로 잡고, 이번 파일에 존재한 종목은 Upsert 이후 `updatedAt = syncStartedAt`으로 갱신한다.

이후 다음 조건을 만족하는 종목을 DELISTED로 갱신한다.

```text
market = KOSPI
updatedAt < syncStartedAt
status != DELISTED
```

이 방식은 기존 값이 파일 내용과 동일해 JPA dirty checking이 발생하지 않는 종목도 이번 파일에 존재했다는 사실을 `updatedAt`에 반영하기 위한 것이다.

파일 다운로드 실패, 압축 해제 실패, 파싱 실패, 빈 파일은 DB 갱신 전에 전체 동기화를 실패 처리한다.

따라서 실패한 파일을 기준으로 기존 종목을 DELISTED 처리하지 않는다.

---

# 스케줄링

애플리케이션 시작 시 1회 수행하고, 이후 정규장 시작 전과 종료 후 하루 2회 수행한다.

```text
ApplicationReadyEvent
08:30 KST
15:50 KST
```

스케줄러는 예외를 잡아 로그를 남기고 다음 실행을 막지 않는다.

서비스는 실패 원인을 error 로그로 남기고 예외를 전파한다.

---

# 성능 고려사항

KOSPI 종목 수는 약 800~1000개 수준이다.

따라서

text 파일 다운로드 → 전체 파싱 → DB Upsert

방식으로 처리해도 충분하다.

복잡한 배치 프레임워크(Spring Batch)는 사용하지 않는다.

---

# 동기화 결과 처리

MVP 단계에서는 별도의 동기화 이력 테이블을 관리하지 않는다.

동기화 결과는 로그로만 기록한다.

예)

text Stock master sync started Stock master sync completed. total=950 Stock master sync failed

---

# 구현 원칙

- KIS 공식 MST 파일을 Source Of Truth로 사용한다.
- 종목 조회는 DB만 사용한다.
- 사용자 요청 시 KIS 종목 API를 호출하지 않는다.
- 종목 마스터 동기화는 스케줄러 기반으로 수행한다.
- 종목 상태는 MST 파일 기준으로 계산한다.
- 파일 파싱 실패 시 전체 동기화를 실패 처리한다.
- 종목코드(stockCode)를 기준으로 Upsert 수행한다.
- 기존 종목 PK 및 연관관계를 유지한다.
