package com.team10.backend.domain.codef.auth.ocr;

/** Tesseract OCR 결과에서 파싱된 신분증 정보(이름/주민등록번호/발급일자). */
public record IdCardOcrResult(
        String name,
        String residentNumber,
        String issueDate
) {
}
