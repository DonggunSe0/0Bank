-- k6 투자 계좌/보유종목 테스트용 데이터입니다.
-- 먼저 아래 데이터가 준비되어 있어야 합니다.
--   1. 01_k6_users_accounts_transactions.sql 실행 완료
--   2. 외부 주식 API로 적재된 실제 stocks 데이터 존재
--
-- 이 파일은 가짜 stocks 데이터를 생성하지 않습니다.
-- 아래 stock_code 값은 확정값이 아니라 확인/교체가 필요한 후보값입니다.
-- AWS/DB 담당자는 EC2 MySQL의 실제 stocks.stock_code 값을 확인한 뒤,
-- 존재하지 않는 코드가 있으면 실제 존재하는 코드로 교체해야 합니다.

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
  -- 확인 필요(AWS/DB): 아래 stock_code가 EC2 MySQL에 없으면 실제 존재하는 stocks.stock_code로 교체하세요.
  SELECT '005930' AS stock_code, 71000.00 AS average_price, 100 AS quantity
  UNION ALL SELECT '000660', 160000.00, 50
  UNION ALL SELECT '035420', 210000.00, 30
  UNION ALL SELECT '035720', 52000.00, 80
  UNION ALL SELECT '005380', 240000.00, 25
  UNION ALL SELECT '068270', 180000.00, 40
  UNION ALL SELECT '373220', 420000.00, 15
  UNION ALL SELECT '207940', 850000.00, 10
  UNION ALL SELECT '051910', 360000.00, 20
  UNION ALL SELECT '006400', 390000.00, 18
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
-- matched_stock_count가 expected_stock_count보다 작으면,
-- 위 stock_code 후보와 perf/k6/.env.local의 STOCK_CODES 값을 실제 존재하는 코드로 바꿔야 합니다.
SELECT
  10 AS expected_stock_count,
  COUNT(*) AS matched_stock_count
FROM stocks
WHERE stock_code IN ('005930', '000660', '035420', '035720', '005380', '068270', '373220', '207940', '051910', '006400');
