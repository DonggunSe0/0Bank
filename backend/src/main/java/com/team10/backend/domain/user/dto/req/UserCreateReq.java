package com.team10.backend.domain.user.dto.req;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UserCreateReq(

        @NotBlank(message = "본인인증 ID는 필수입니다.")
        String identityVerificationId,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 영문과 숫자를 각각 1자 이상 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = "^01[0-9]{8,9}$", message = "휴대폰 번호 형식이 올바르지 않습니다.")
        String phoneNumber,

        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate,

        // 약관 동의 — 필수
        @NotNull(message = "서비스 이용약관 동의는 필수입니다.")
        @AssertTrue(message = "서비스 이용약관에 동의해야 합니다.")
        Boolean agreedServiceTerms,

        @NotNull(message = "개인정보 수집·이용 동의는 필수입니다.")
        @AssertTrue(message = "개인정보 수집·이용에 동의해야 합니다.")
        Boolean agreedPersonalInfo,

        @NotNull(message = "금융정보 수집·이용 동의는 필수입니다.")
        @AssertTrue(message = "금융정보 수집·이용에 동의해야 합니다.")
        Boolean agreedFinancialInfo,

        // 약관 동의 — 선택
        Boolean agreedMarketing
) {
}
