ALTER TABLE `users`
    ADD COLUMN `identity_verified_at` datetime(6) DEFAULT NULL;

-- 이미 본인인증을 완료한 사용자는 마지막 COMPLETED 시점(identity_verifications.updated_at)으로 백필.
-- 이후 User.isIdentityVerificationValid()가 이 시각 기준 30일 경과 여부로 만료를 판단한다.
UPDATE `users` u
JOIN (
    SELECT `user_id`, MAX(`updated_at`) AS `completed_at`
    FROM `identity_verifications`
    WHERE `status` = 'COMPLETED'
    GROUP BY `user_id`
) iv ON iv.`user_id` = u.`id`
SET u.`identity_verified_at` = iv.`completed_at`
WHERE u.`identity_verified` = 1;
