package com.team10.backend.domain.investment.account.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvestmentAccountUpdateReq(
        @Schema(description = "현재 투자 계좌 비밀번호. 숫자 6자리")
        @NotBlank(message = "현재 투자 계좌 비밀번호는 필수입니다.")
        @Pattern(regexp = "\\d{6}", message = "현재 투자 계좌 비밀번호는 숫자 6자리여야 합니다.")
        String pastPassword,

        @Schema(description = "변경할 투자 계좌 별칭. 전달하지 않으면 수정하지 않습니다.", nullable = true)
        @Size(max = 50, message = "투자 계좌 별칭은 50자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "투자 계좌 별칭은 공백일 수 없습니다.")
        String nickname,

        @Schema(description = "새 투자 계좌 비밀번호. 숫자 6자리. 전달하지 않으면 수정하지 않습니다.", nullable = true)
        @Pattern(regexp = "\\d{6}", message = "새 투자 계좌 비밀번호는 숫자 6자리여야 합니다.")
        String newPassword
) {

    /**
     * 필드에 대한 Bean Validation 통과 후 수행되는 객체 단위 검증 메서드
     */
    @JsonIgnore
    @AssertTrue(message = "수정할 투자 계좌 정보는 하나 이상 필요합니다.")
    public boolean hasUpdateValue() {
        return nickname != null || newPassword != null;
    }
}
