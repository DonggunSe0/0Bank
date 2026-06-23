# Team10 Banking MVP Docs

이 폴더는 최소 MVP 범위인 회원가입/로그인, 사용자 정보 조회, 본인인증, 계좌 개설, 입금/송금, 거래내역 조회 기능의 기획/개발 기준 문서를 관리합니다.

## 문서 목록

- [기능 명세서](./functional-spec.md)
- [API 명세서](./api-spec.md)
- [API 명세서 - 표 형태 요약본](./api-spec-table.md)
- [2026-06-21 외부 계좌 민감정보 보호 작업 정리](../backend/docs/2026-06-21-external-account-security-work.md)
- [CODEF account-inquiry 외부계좌 연동 13단계 실행 계획](./codef-account-inquiry-13-step-plan.md)

## MVP 우선순위

1. Auth: 회원가입, 로그인, 사용자 정보 조회, 본인인증
2. Account: 계좌 개설 및 내 계좌 조회
3. Transfer: 입금, 계좌 간 송금
4. Transaction: 거래내역 조회 및 필터링

## 공통 전제

- Backend base URL: `http://localhost:8080`
- 인증 방식은 MVP 기준 `Authorization: Bearer {accessToken}` 토큰 인증을 사용한다고 가정합니다.
- 응답/에러 포맷은 현재 백엔드의 `global.exception.ErrorResponse` 구조와 맞춥니다.
