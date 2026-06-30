-- k6 성능/부하테스트용 샘플 데이터 정리 스크립트입니다.
-- 번호가 붙은 seed SQL을 다시 실행하기 전에 이 파일을 먼저 실행해도 됩니다.

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM idempotency_keys
WHERE user_id BETWEEN 900001 AND 900099;

DELETE FROM fx_wallet_ledgers
WHERE fx_wallet_id BETWEEN 900001 AND 900099
   OR exchange_order_id BETWEEN 900001 AND 999999;

DELETE FROM exchange_orders
WHERE id BETWEEN 900001 AND 999999
   OR user_id BETWEEN 900001 AND 900099
   OR krw_account_id BETWEEN 900001 AND 900099
   OR fx_wallet_id BETWEEN 900001 AND 900099;

DELETE FROM exchange_quotes
WHERE id BETWEEN 900001 AND 999999
   OR user_id BETWEEN 900001 AND 900099;

DELETE FROM investment_trades
WHERE investment_account_id BETWEEN 900001 AND 900099
   OR idempotency_key LIKE 'k6_%';

DELETE FROM investment_holdings
WHERE investment_account_id BETWEEN 900001 AND 900099;

DELETE FROM investment_accounts
WHERE id BETWEEN 900001 AND 900099;

DELETE FROM stock_watchlists
WHERE user_id BETWEEN 900001 AND 900099;

DELETE FROM transaction_histories
WHERE account_id BETWEEN 900001 AND 900099;

DELETE FROM transfers
WHERE sender_account_id BETWEEN 900001 AND 900099
   OR receiver_account_id BETWEEN 900001 AND 900099;

DELETE FROM external_asset_transactions
WHERE external_account_id BETWEEN 900001 AND 900099;

DELETE FROM external_account
WHERE id BETWEEN 900001 AND 900099
   OR user_id BETWEEN 900001 AND 900099
   OR account_number_hash LIKE 'k6_%';

DELETE FROM codef_external_account_connection
WHERE user_id BETWEEN 900001 AND 900099;

DELETE FROM user_profile_interests
WHERE profile_id BETWEEN 900001 AND 900099;

DELETE FROM user_profiles
WHERE id BETWEEN 900001 AND 900099
   OR user_id BETWEEN 900001 AND 900099;

DELETE FROM user_consents
WHERE user_id BETWEEN 900001 AND 900099;

DELETE FROM identity_verifications
WHERE user_id BETWEEN 900001 AND 900099;

DELETE FROM fx_wallets
WHERE id BETWEEN 900001 AND 900099
   OR user_id BETWEEN 900001 AND 900099;

DELETE FROM accounts
WHERE id BETWEEN 900001 AND 900099;

DELETE FROM users
WHERE id BETWEEN 900001 AND 900099
   OR email LIKE 'k6-%@0bank.test';

-- stocks, young_policy, currencies, exchange_rates는 기준 데이터 또는 외부 API 적재 데이터입니다.
-- 기존 실제 데이터는 삭제하지 않습니다.
-- seed SQL은 k6 전용 사용자/계좌/지갑 데이터만 만들고,
-- 주식/청년정책은 EC2 MySQL에 이미 적재된 실제 데이터를 조회해서 사용합니다.

SET FOREIGN_KEY_CHECKS = 1;
