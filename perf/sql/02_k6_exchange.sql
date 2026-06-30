-- k6 환율/환전 테스트용 데이터입니다.
-- 먼저 01_k6_users_accounts_transactions.sql을 실행해야 합니다.

INSERT INTO currencies
(decimal_places, created_at, updated_at, country_name, currency_name, currency_code, status)
VALUES
(0, NOW(6), NOW(6), '대한민국', '원', 'KRW', 'ACTIVE'),
(2, NOW(6), NOW(6), '미국', '달러', 'USD', 'ACTIVE'),
(0, NOW(6), NOW(6), '일본', '엔', 'JPY', 'ACTIVE'),
(2, NOW(6), NOW(6), '유럽연합', '유로', 'EUR', 'ACTIVE'),
(2, NOW(6), NOW(6), '중국', '위안', 'CNY', 'ACTIVE'),
(2, NOW(6), NOW(6), '영국', '파운드', 'GBP', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  decimal_places = VALUES(decimal_places),
  updated_at = NOW(6),
  country_name = VALUES(country_name),
  currency_name = VALUES(currency_name),
  status = VALUES(status);

INSERT INTO exchange_rates
(base_price, currency_unit, created_at, rate_at, updated_at, currency_code)
VALUES
(1.000000, 1, NOW(6), NOW(6), NOW(6), 'KRW'),
(1380.000000, 1, NOW(6), NOW(6), NOW(6), 'USD'),
(920.000000, 100, NOW(6), NOW(6), NOW(6), 'JPY'),
(1480.000000, 1, NOW(6), NOW(6), NOW(6), 'EUR'),
(190.000000, 1, NOW(6), NOW(6), NOW(6), 'CNY'),
(1720.000000, 1, NOW(6), NOW(6), NOW(6), 'GBP')
ON DUPLICATE KEY UPDATE
  base_price = VALUES(base_price),
  currency_unit = VALUES(currency_unit),
  rate_at = VALUES(rate_at),
  updated_at = NOW(6);

INSERT INTO fx_wallets
(id, balance, created_at, updated_at, user_id, currency_code, status)
VALUES
(900001, 10000.0000, NOW(6), NOW(6), 900001, 'USD', 'ACTIVE'),
(900002, 10000.0000, NOW(6), NOW(6), 900002, 'USD', 'ACTIVE'),
(900003, 10000.0000, NOW(6), NOW(6), 900003, 'USD', 'ACTIVE'),
(900004, 10000.0000, NOW(6), NOW(6), 900004, 'USD', 'ACTIVE'),
(900005, 10000.0000, NOW(6), NOW(6), 900005, 'USD', 'ACTIVE'),
(900006, 10000.0000, NOW(6), NOW(6), 900006, 'USD', 'ACTIVE'),
(900007, 10000.0000, NOW(6), NOW(6), 900007, 'USD', 'ACTIVE'),
(900008, 10000.0000, NOW(6), NOW(6), 900008, 'USD', 'ACTIVE'),
(900009, 10000.0000, NOW(6), NOW(6), 900009, 'USD', 'ACTIVE'),
(900010, 10000.0000, NOW(6), NOW(6), 900010, 'USD', 'ACTIVE'),
(900011, 10000.0000, NOW(6), NOW(6), 900011, 'USD', 'ACTIVE'),
(900012, 10000.0000, NOW(6), NOW(6), 900012, 'USD', 'ACTIVE'),
(900013, 10000.0000, NOW(6), NOW(6), 900013, 'USD', 'ACTIVE'),
(900014, 10000.0000, NOW(6), NOW(6), 900014, 'USD', 'ACTIVE'),
(900015, 10000.0000, NOW(6), NOW(6), 900015, 'USD', 'ACTIVE'),
(900016, 10000.0000, NOW(6), NOW(6), 900016, 'USD', 'ACTIVE'),
(900017, 10000.0000, NOW(6), NOW(6), 900017, 'USD', 'ACTIVE'),
(900018, 10000.0000, NOW(6), NOW(6), 900018, 'USD', 'ACTIVE'),
(900019, 10000.0000, NOW(6), NOW(6), 900019, 'USD', 'ACTIVE'),
(900020, 10000.0000, NOW(6), NOW(6), 900020, 'USD', 'ACTIVE')
ON DUPLICATE KEY UPDATE
  balance = VALUES(balance),
  updated_at = NOW(6),
  status = VALUES(status);
