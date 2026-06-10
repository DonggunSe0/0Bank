package com.team10.backend.domain.user.ocr;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 원문 텍스트에서 신분증 핵심 정보를 추출하는 정규표현식 파서.
 *
 * <p>지원 신분증: 주민등록증, 운전면허증
 *
 * <p>추출 항목
 * <ul>
 *   <li>이름 — 한글 2~4자 (성명 레이블 다음에 등장하거나 독립 패턴으로 탐지)</li>
 *   <li>주민등록번호 — 앞 6자리 + '-' + 뒤 7자리, 뒷자리 첫 번째 숫자 1~4</li>
 *   <li>발급일자 — yyyy.MM.dd / yyyy-MM-dd / yyyy MM dd 형식, yyyy-MM-dd 로 정규화</li>
 * </ul>
 */
@Component
public class IdCardParser {

    /**
     * 주민등록번호: 6자리-7자리 (뒷자리 첫 숫자 1~4)
     * 예: 901201-1234567
     */
    private static final Pattern RESIDENT_NUMBER_PATTERN =
            Pattern.compile("(\\d{6})[\\-–](([1-4])\\d{6})");

    /**
     * 발급일자: yyyy.MM.dd / yyyy-MM-dd / yyyy MM dd / yyyy. MM. dd. 형식
     * 구분자가 점+공백(". ") 두 글자인 경우도 처리
     * 예: 2024. 11. 21. / 2023.01.15 / 2023-01-15
     */
    private static final Pattern ISSUE_DATE_PATTERN =
            Pattern.compile("(20\\d{2})[.\\-\\s]{1,2}(\\d{1,2})[.\\-\\s]{1,2}(\\d{1,2})");

    /**
     * 이름: '성명' 또는 '이름' 레이블 뒤의 한글 2~4자
     * 예: 성명 홍길동
     */
    private static final Pattern NAME_LABELED_PATTERN =
            Pattern.compile("(?:성명|이름)[\\s:：]*([가-힣]{2,4})");

    /**
     * 이름: '주민등록증' 헤더 바로 다음 줄의 한글 2~4자
     * 예: "주민등록증\n강원석(錫)" → "강원석"
     */
    private static final Pattern NAME_AFTER_HEADER_PATTERN =
            Pattern.compile("주민등록증\\s+([가-힣]{2,4})");

    /**
     * 이름 독립 패턴: 줄바꿈/공백 뒤 한글 2~4자, 뒤에 공백·개행·'('·끝이 오는 경우
     * '강원석(錫)' 처럼 한자 주석이 붙는 경우도 처리
     * (레이블·헤더 패턴 실패 시 폴백)
     */
    private static final Pattern NAME_STANDALONE_PATTERN =
            Pattern.compile("(?:^|\\n)([가-힣]{2,4})(?:\\s|\\(|$)", Pattern.MULTILINE);

    /**
     * OCR 원문 텍스트를 파싱하여 신분증 정보를 추출한다.
     *
     * @param rawText Tesseract가 추출한 원문 텍스트
     * @return 파싱 성공 시 {@link IdCardOcrResult}, 실패 시 {@link Optional#empty()}
     */
    public Optional<IdCardOcrResult> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        Optional<String> residentNumber = extractResidentNumber(rawText);
        Optional<String> issueDate = extractIssueDate(rawText);
        Optional<String> name = extractName(rawText);

        if (residentNumber.isEmpty() || issueDate.isEmpty() || name.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new IdCardOcrResult(
                name.get(),
                residentNumber.get(),
                issueDate.get()
        ));
    }

    private Optional<String> extractResidentNumber(String text) {
        Matcher matcher = RESIDENT_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1) + "-" + matcher.group(2));
        }
        return Optional.empty();
    }

    private Optional<String> extractIssueDate(String text) {
        Matcher matcher = ISSUE_DATE_PATTERN.matcher(text);
        if (matcher.find()) {
            String year  = matcher.group(1);
            String month = String.format("%02d", Integer.parseInt(matcher.group(2)));
            String day   = String.format("%02d", Integer.parseInt(matcher.group(3)));
            return Optional.of(year + "-" + month + "-" + day);
        }
        return Optional.empty();
    }

    private Optional<String> extractName(String text) {
        // 1차: '성명' / '이름' 레이블 기반
        Matcher labeled = NAME_LABELED_PATTERN.matcher(text);
        if (labeled.find()) {
            return Optional.of(labeled.group(1));
        }

        // 2차: '주민등록증' 헤더 다음 줄 패턴 (주민등록증에서 가장 신뢰도 높음)
        Matcher afterHeader = NAME_AFTER_HEADER_PATTERN.matcher(text);
        if (afterHeader.find()) {
            return Optional.of(afterHeader.group(1));
        }

        // 3차: 독립 한글 패턴 폴백
        Matcher standalone = NAME_STANDALONE_PATTERN.matcher(text);
        if (standalone.find()) {
            return Optional.of(standalone.group(1).trim());
        }

        return Optional.empty();
    }
}
