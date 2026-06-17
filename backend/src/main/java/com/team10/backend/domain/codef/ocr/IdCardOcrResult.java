package com.team10.backend.domain.codef.ocr;

/**
 * Tesseract OCR 결과에서 파싱된 신분증 정보.
 *
 * @param name           이름 (예: 홍길동)
 * @param residentNumber 주민등록번호 (예: 901201-1234567)
 * @param issueDate      발급일자 정규화 문자열 (예: 2023-01-15)
 */
public record IdCardOcrResult(
        String name,
        String residentNumber,
        String issueDate
) {
}
