-- k6 은행/계좌 조회 테스트용 기본 데이터입니다.
-- 테스트 사용자 20명, 입출금 계좌 40개, 계좌별 거래내역 20,000건을 생성합니다.
-- 로그인 비밀번호 평문: Password1!
-- 계좌 비밀번호 평문: 123456

SET @k6_user_password_hash = '$2y$10$MT4iEl8KDbq/n4iQB8uUYeGHAXlrn9wUH1metPptpZgxg8ORo4X.O';
SET @k6_account_password_hash = '$2y$10$JLOUv1hc6oGsQYLvePh8FOBuoWZRgbRnUgdwdY3pl81J9TmopxBTO';

INSERT INTO users
(id, birth_date, identity_verified, identity_verified_at, created_at, updated_at, phone_number, name, email, password, status)
SELECT
  900000 + user_seq.n AS id,
  DATE_ADD('1990-01-01', INTERVAL user_seq.n DAY) AS birth_date,
  1 AS identity_verified,
  NOW(6) AS identity_verified_at,
  NOW(6) AS created_at,
  NOW(6) AS updated_at,
  CONCAT('0109000', LPAD(user_seq.n, 4, '0')) AS phone_number,
  CONCAT('k6 테스트 사용자 ', user_seq.n) AS name,
  CONCAT('k6-user', user_seq.n, '@0bank.test') AS email,
  @k6_user_password_hash AS password,
  'ACTIVE' AS status
FROM (
  SELECT ones.i + tens.i * 10 + 1 AS n
  FROM
    (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1) tens
) user_seq
WHERE user_seq.n <= 20
ON DUPLICATE KEY UPDATE
  birth_date = VALUES(birth_date),
  identity_verified = VALUES(identity_verified),
  identity_verified_at = VALUES(identity_verified_at),
  updated_at = NOW(6),
  phone_number = VALUES(phone_number),
  name = VALUES(name),
  password = VALUES(password),
  status = VALUES(status);

INSERT INTO identity_verifications
(id, created_at, updated_at, user_id, ocr_issue_date, ocr_resident_number, ocr_resident_number_hash, ocr_name, failure_reason, status)
SELECT
  900000 + user_seq.n AS id,
  NOW(6) AS created_at,
  NOW(6) AS updated_at,
  900000 + user_seq.n AS user_id,
  '20200101' AS ocr_issue_date,
  NULL AS ocr_resident_number,
  CONCAT('k6_identity_hash_', 900000 + user_seq.n) AS ocr_resident_number_hash,
  CONCAT('k6 테스트 사용자 ', user_seq.n) AS ocr_name,
  NULL AS failure_reason,
  'COMPLETED' AS status
FROM (
  SELECT ones.i + tens.i * 10 + 1 AS n
  FROM
    (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1) tens
) user_seq
WHERE user_seq.n <= 20
ON DUPLICATE KEY UPDATE
  updated_at = NOW(6),
  status = VALUES(status);

INSERT INTO user_profiles
(id, created_at, updated_at, user_id, region, age_group, occupation_status)
SELECT
  900000 + user_seq.n AS id,
  NOW(6) AS created_at,
  NOW(6) AS updated_at,
  900000 + user_seq.n AS user_id,
  CASE WHEN user_seq.n % 2 = 0 THEN 'GYEONGGI' ELSE 'SEOUL' END AS region,
  'TWENTIES' AS age_group,
  CASE WHEN user_seq.n % 3 = 0 THEN 'EMPLOYED' ELSE 'STUDENT' END AS occupation_status
FROM (
  SELECT ones.i + tens.i * 10 + 1 AS n
  FROM
    (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1) tens
) user_seq
WHERE user_seq.n <= 20
ON DUPLICATE KEY UPDATE
  updated_at = NOW(6),
  region = VALUES(region),
  age_group = VALUES(age_group),
  occupation_status = VALUES(occupation_status);

INSERT INTO user_consents
(agreed, agreed_at, created_at, updated_at, user_id, terms_type)
SELECT
  1 AS agreed,
  NOW(6) AS agreed_at,
  NOW(6) AS created_at,
  NOW(6) AS updated_at,
  900000 + user_seq.n AS user_id,
  terms.terms_type
FROM (
  SELECT ones.i + tens.i * 10 + 1 AS n
  FROM
    (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1) tens
) user_seq
CROSS JOIN (
  SELECT 'SERVICE_TERMS' AS terms_type
  UNION ALL SELECT 'PERSONAL_INFO'
  UNION ALL SELECT 'FINANCIAL_INFO'
) terms
WHERE user_seq.n <= 20
ON DUPLICATE KEY UPDATE
  agreed = VALUES(agreed),
  agreed_at = VALUES(agreed_at),
  updated_at = NOW(6);

INSERT INTO accounts
(id, balance, created_at, updated_at, user_id, account_number, nickname, account_type, account_password_hash, status)
SELECT
  900000 + ((user_seq.n - 1) * 2) + account_slot.n AS id,
  1000000000 AS balance,
  NOW(6) AS created_at,
  NOW(6) AS updated_at,
  900000 + user_seq.n AS user_id,
  CONCAT('900', LPAD(((user_seq.n - 1) * 2) + account_slot.n, 9, '0')) AS account_number,
  CONCAT('k6 user', user_seq.n, CASE WHEN account_slot.n = 1 THEN ' main' ELSE ' sub' END) AS nickname,
  'DEPOSIT' AS account_type,
  @k6_account_password_hash AS account_password_hash,
  'ACTIVE' AS status
FROM (
  SELECT ones.i + tens.i * 10 + 1 AS n
  FROM
    (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1) tens
) user_seq
CROSS JOIN (
  SELECT 1 AS n
  UNION ALL SELECT 2
) account_slot
WHERE user_seq.n <= 20
ON DUPLICATE KEY UPDATE
  balance = VALUES(balance),
  updated_at = NOW(6),
  user_id = VALUES(user_id),
  nickname = VALUES(nickname),
  account_type = VALUES(account_type),
  account_password_hash = VALUES(account_password_hash),
  status = VALUES(status);

INSERT INTO transaction_histories
(account_id, amount, balance_after, balance_before, created_at, transacted_at, transfer_id, updated_at,
 counterparty_account_number, counterparty_name, memo, direction, type)
SELECT
  a.id,
  1000 + (seq.n % 100000) AS amount,
  a.balance + seq.n AS balance_after,
  a.balance + seq.n - (1000 + (seq.n % 100000)) AS balance_before,
  DATE_SUB(NOW(6), INTERVAL seq.n MINUTE) AS created_at,
  DATE_SUB(NOW(6), INTERVAL seq.n MINUTE) AS transacted_at,
  NULL AS transfer_id,
  DATE_SUB(NOW(6), INTERVAL seq.n MINUTE) AS updated_at,
  CONCAT('800', LPAD((seq.n % 1000000000), 9, '0')) AS counterparty_account_number,
  CONCAT('k6 거래처 ', seq.n % 200) AS counterparty_name,
  CONCAT('k6 거래내역 ', a.id, '-', seq.n) AS memo,
  CASE WHEN seq.n % 2 = 0 THEN 'IN' ELSE 'OUT' END AS direction,
  CASE
    WHEN seq.n % 5 = 0 THEN 'TRANSFER'
    WHEN seq.n % 5 = 1 THEN 'PAYMENT'
    ELSE 'DEPOSIT'
  END AS type
FROM accounts a
JOIN (
  SELECT ones.i + tens.i * 10 + hundreds.i * 100 + thousands.i * 1000 + ten_thousands.i * 10000 + 1 AS n
  FROM
    (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) thousands
    CROSS JOIN (SELECT 0 i UNION ALL SELECT 1) ten_thousands
) seq
WHERE a.id BETWEEN 900001 AND 900040
  AND seq.n <= 20000;
