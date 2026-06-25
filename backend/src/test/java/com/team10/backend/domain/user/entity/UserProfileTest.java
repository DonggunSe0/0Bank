package com.team10.backend.domain.user.entity;

import com.team10.backend.domain.user.type.AgeGroup;
import com.team10.backend.domain.user.type.FinancialInterest;
import com.team10.backend.domain.user.type.OccupationStatus;
import com.team10.backend.domain.user.type.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileTest {

    private User newUser() {
        return User.create("test@example.com", "encoded-pw", "홍길동", "01012345678", LocalDate.of(1990, 1, 1));
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("관심사가 주어지면 그대로 설정된다")
        void withInterests_setsAsGiven() {
            UserProfile profile = UserProfile.create(
                    newUser(), AgeGroup.TWENTIES, Region.SEOUL, OccupationStatus.EMPLOYED,
                    Set.of(FinancialInterest.SAVINGS, FinancialInterest.INVESTMENT)
            );

            assertThat(profile.getAgeGroup()).isEqualTo(AgeGroup.TWENTIES);
            assertThat(profile.getRegion()).isEqualTo(Region.SEOUL);
            assertThat(profile.getOccupationStatus()).isEqualTo(OccupationStatus.EMPLOYED);
            assertThat(profile.getFinancialInterests())
                    .containsExactlyInAnyOrder(FinancialInterest.SAVINGS, FinancialInterest.INVESTMENT);
        }

        @Test
        @DisplayName("관심사가 null이면 빈 집합으로 초기화된다")
        void nullInterests_initializesEmptySet() {
            UserProfile profile = UserProfile.create(
                    newUser(), AgeGroup.THIRTIES, Region.BUSAN, OccupationStatus.STUDENT, null
            );

            assertThat(profile.getFinancialInterests()).isEmpty();
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("필드와 관심사 집합이 새 값으로 교체된다")
        void replacesFieldsAndInterests() {
            UserProfile profile = UserProfile.create(
                    newUser(), AgeGroup.TWENTIES, Region.SEOUL, OccupationStatus.EMPLOYED,
                    new HashSet<>(Set.of(FinancialInterest.SAVINGS))
            );

            profile.update(AgeGroup.FORTIES, Region.DAEGU, OccupationStatus.FREELANCER,
                    Set.of(FinancialInterest.LOAN, FinancialInterest.PENSION));

            assertThat(profile.getAgeGroup()).isEqualTo(AgeGroup.FORTIES);
            assertThat(profile.getRegion()).isEqualTo(Region.DAEGU);
            assertThat(profile.getOccupationStatus()).isEqualTo(OccupationStatus.FREELANCER);
            assertThat(profile.getFinancialInterests())
                    .containsExactlyInAnyOrder(FinancialInterest.LOAN, FinancialInterest.PENSION);
        }

        @Test
        @DisplayName("관심사로 null이 전달되면 기존 관심사를 비우기만 한다")
        void nullInterests_clearsExisting() {
            UserProfile profile = UserProfile.create(
                    newUser(), AgeGroup.TWENTIES, Region.SEOUL, OccupationStatus.EMPLOYED,
                    new HashSet<>(Set.of(FinancialInterest.SAVINGS))
            );

            profile.update(AgeGroup.TWENTIES, Region.SEOUL, OccupationStatus.EMPLOYED, null);

            assertThat(profile.getFinancialInterests()).isEmpty();
        }
    }
}
