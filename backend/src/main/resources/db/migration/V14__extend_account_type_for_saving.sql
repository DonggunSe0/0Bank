ALTER TABLE accounts
    MODIFY COLUMN account_type enum('DEPOSIT', 'SAVING_DEPOSIT', 'SAVING_INSTALLMENT') NOT NULL;

ALTER TABLE deposits
    ADD COLUMN saving_account_id bigint NULL;

ALTER TABLE installments
    ADD COLUMN saving_account_id bigint NULL;

INSERT INTO accounts
(balance, created_at, updated_at, user_id, account_number, nickname, account_type, account_password_hash, status)
SELECT
    CASE WHEN d.status = 'ACTIVE' THEN d.principal ELSE 0 END,
    COALESCE(d.created_at, NOW(6)),
    COALESCE(d.updated_at, NOW(6)),
    d.user_id,
    CONCAT('SAVD', LPAD(d.id, 26, '0')),
    '예금 전용 계좌',
    'SAVING_DEPOSIT',
    NULL,
    CASE WHEN d.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'CLOSED' END
FROM deposits d
WHERE d.saving_account_id IS NULL;

UPDATE deposits d
JOIN accounts a ON a.account_number = CONCAT('SAVD', LPAD(d.id, 26, '0'))
SET d.saving_account_id = a.id
WHERE d.saving_account_id IS NULL;

INSERT INTO accounts
(balance, created_at, updated_at, user_id, account_number, nickname, account_type, account_password_hash, status)
SELECT
    CASE WHEN i.status IN ('ACTIVE', 'PAYMENT_FAILED') THEN i.paid_amount ELSE 0 END,
    COALESCE(i.created_at, NOW(6)),
    COALESCE(i.updated_at, NOW(6)),
    i.user_id,
    CONCAT('SAVI', LPAD(i.id, 26, '0')),
    '적금 전용 계좌',
    'SAVING_INSTALLMENT',
    NULL,
    CASE WHEN i.status IN ('ACTIVE', 'PAYMENT_FAILED') THEN 'ACTIVE' ELSE 'CLOSED' END
FROM installments i
WHERE i.saving_account_id IS NULL;

UPDATE installments i
JOIN accounts a ON a.account_number = CONCAT('SAVI', LPAD(i.id, 26, '0'))
SET i.saving_account_id = a.id
WHERE i.saving_account_id IS NULL;

ALTER TABLE deposits
    MODIFY COLUMN saving_account_id bigint NOT NULL;

ALTER TABLE deposits
    ADD CONSTRAINT fk_deposits_saving_account
        FOREIGN KEY (saving_account_id) REFERENCES accounts (id);

ALTER TABLE deposits
    ADD CONSTRAINT uk_deposits_saving_account_id UNIQUE (saving_account_id);

ALTER TABLE installments
    MODIFY COLUMN saving_account_id bigint NOT NULL;

ALTER TABLE installments
    ADD CONSTRAINT fk_installments_saving_account
        FOREIGN KEY (saving_account_id) REFERENCES accounts (id);

ALTER TABLE installments
    ADD CONSTRAINT uk_installments_saving_account_id UNIQUE (saving_account_id);

ALTER TABLE transaction_histories
    MODIFY COLUMN type enum(
    'DEPOSIT',
    'TRANSFER',
    'PAYMENT',
    'EXCHANGE',
    'SAVING_DEPOSIT_SIGNUP',
    'SAVING_INSTALLMENT_SIGNUP',
    'SAVING_CANCEL_REFUND',
    'SAVING_MATURITY',
    'INSTALLMENT_PAYMENT'
    ) NOT NULL;

ALTER TABLE deposits
    DROP COLUMN withdrawal_locked,
    DROP COLUMN withdrawal_lock_reason;

ALTER TABLE installments
    DROP COLUMN withdrawal_locked,
    DROP COLUMN withdrawal_lock_reason;
