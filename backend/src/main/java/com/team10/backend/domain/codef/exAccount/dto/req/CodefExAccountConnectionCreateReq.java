package com.team10.backend.domain.codef.exAccount.dto.req;

import com.team10.backend.domain.codef.exAccount.validation.ValidCodefExAccountConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "CODEF 외부계좌 기관 연결 요청")
@ValidCodefExAccountConnection
public record CodefExAccountConnectionCreateReq(
        @Schema(description = "CODEF 기관코드", example = "0004", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "기관코드는 필수입니다.")
        @Size(min = 4, max = 4, message = "기관코드는 4자리여야 합니다.")
        String organization,

        @Schema(description = "CODEF 업무 구분", example = "BK", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "업무 구분은 필수입니다.")
        @Size(max = 2, message = "업무 구분은 2자 이하여야 합니다.")
        String businessType,

        @Schema(description = "고객 구분", example = "P", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "고객 구분은 필수입니다.")
        @Size(max = 1, message = "고객 구분은 1자 이하여야 합니다.")
        String clientType,

        @Schema(description = "로그인 방식", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "로그인 방식은 필수입니다.")
        @Size(max = 1, message = "로그인 방식은 1자 이하여야 합니다.")
        String loginType,

        @Schema(description = "은행 인터넷뱅킹 로그인 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "로그인 ID는 필수입니다.")
        @Size(max = 100, message = "로그인 ID는 100자 이하여야 합니다.")
        String loginId,

        @Schema(description = "은행 인터넷뱅킹 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 200, message = "비밀번호는 200자 이하여야 합니다.")
        String password,

        @Schema(description = "생년월일(YYMMDD)", example = "990101", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "생년월일은 필수입니다.")
        @Size(min = 6, max = 6, message = "생년월일은 YYMMDD 형식이어야 합니다.")
        String birthDate
) {

    @Override
    public String toString() {
        return "CodefExAccountConnectionCreateReq[organization=" + organization
                + ", businessType=" + businessType
                + ", clientType=" + clientType
                + ", loginType=" + loginType
                + ", loginId=<redacted>"
                + ", password=<redacted>"
                + ", birthDate=<redacted>]";
    }
}
