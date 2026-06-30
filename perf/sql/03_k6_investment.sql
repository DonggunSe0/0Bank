-- k6 투자 계좌/보유종목 테스트용 데이터입니다.
-- 먼저 아래 데이터가 준비되어 있어야 합니다.
--   1. 01_k6_users_accounts_transactions.sql 실행 완료
--   2. 외부 주식 API로 적재된 실제 stocks 데이터 존재
--
-- 이 파일은 가짜 stocks 데이터를 생성하지 않습니다.
-- 아래 stock_code 값은 EC2 MySQL의 실제 stocks.stock_code 기준으로 확인한 값입니다.

SET @k6_account_password_hash = '$2y$10$JLOUv1hc6oGsQYLvePh8FOBuoWZRgbRnUgdwdY3pl81J9TmopxBTO';

INSERT INTO investment_accounts
(id, cash_balance, created_at, updated_at, user_id, account_number, nickname, account_password_hash, currency_code, status)
VALUES
(900001, 500000000, NOW(6), NOW(6), 900001, '9000000001-01', 'k6 user1 investment', @k6_account_password_hash, 'KRW', 'ACTIVE'),
(900002, 500000000, NOW(6), NOW(6), 900002, '9000000002-01', 'k6 user2 investment', @k6_account_password_hash, 'KRW', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  cash_balance = VALUES(cash_balance),
  updated_at = NOW(6),
  nickname = VALUES(nickname),
  account_password_hash = VALUES(account_password_hash),
  status = VALUES(status);

INSERT INTO investment_holdings
(average_price, created_at, investment_account_id, quantity, stock_id, updated_at)
SELECT
  seed.average_price,
  NOW(6),
  account_seed.investment_account_id,
  seed.quantity,
  s.id,
  NOW(6)
FROM (
  SELECT '000150' AS stock_code, 50603.00 AS average_price, 100 AS quantity
  UNION ALL SELECT '000120', 32145.00, 80
  UNION ALL SELECT '000140', 5883.00, 120
  UNION ALL SELECT '000880', 214514.00, 40
  UNION ALL SELECT '005830', 71054.00, 70
  UNION ALL SELECT '010950', 89427.00, 60
  UNION ALL SELECT '015760', 243985.00, 30
  UNION ALL SELECT '016360', 71227.00, 70
  UNION ALL SELECT '017670', 43923.00, 90
  UNION ALL SELECT '0204S0', 10000.00, 50
) seed
JOIN stocks s ON s.stock_code = seed.stock_code
JOIN (
  SELECT 900001 AS investment_account_id
  UNION ALL SELECT 900002
) account_seed ON TRUE
JOIN investment_accounts ia ON ia.id = account_seed.investment_account_id
ON DUPLICATE KEY UPDATE
  average_price = VALUES(average_price),
  quantity = VALUES(quantity),
  updated_at = NOW(6);

-- 실행 후 확인용 쿼리입니다.
-- matched_stock_count가 expected_stock_count보다 작으면 위 stock_code 값을 다시 확인해야 합니다.
SELECT
  10 AS expected_stock_count,
  COUNT(*) AS matched_stock_count
FROM stocks
WHERE stock_code IN ('000150', '000120', '000140', '000880', '005830', '010950', '015760', '016360', '017670', '0204S0');
