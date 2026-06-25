ALTER TABLE deposits
    ADD COLUMN withdrawal_locked bit(1) NULL,
    ADD COLUMN withdrawal_lock_reason varchar(255) NULL;

UPDATE deposits
SET withdrawal_locked = b'0'
WHERE withdrawal_locked IS NULL;

ALTER TABLE deposits
    MODIFY COLUMN withdrawal_locked bit(1) NOT NULL DEFAULT b'0';

ALTER TABLE installments
    ADD COLUMN next_payment_date date NULL,
    ADD COLUMN withdrawal_locked bit(1) NULL,
    ADD COLUMN withdrawal_lock_reason varchar(255) NULL,
    ADD COLUMN payment_retry_count int NULL,
    ADD COLUMN next_payment_retry_date date NULL,
    ADD COLUMN last_payment_failed_date date NULL,
    ADD COLUMN payment_failure_reason varchar(255) NULL;

UPDATE installments
SET next_payment_date = DATE_ADD(COALESCE(created_at, CURRENT_DATE), INTERVAL 1 MONTH)
WHERE next_payment_date IS NULL;

UPDATE installments
SET withdrawal_locked = b'0'
WHERE withdrawal_locked IS NULL;

UPDATE installments
SET payment_retry_count = 0
WHERE payment_retry_count IS NULL;

ALTER TABLE installments
    MODIFY COLUMN next_payment_date date NOT NULL,
    MODIFY COLUMN withdrawal_locked bit(1) NOT NULL DEFAULT b'0',
    MODIFY COLUMN payment_retry_count int NOT NULL DEFAULT 0;

ALTER TABLE installments
    MODIFY COLUMN status enum('ACTIVE','MATURED','CANCELLED','PAYMENT_FAILED') NOT NULL;

ALTER TABLE transaction_histories
    MODIFY COLUMN type enum(
    'DEPOSIT',
    'TRANSFER',
    'PAYMENT',
    'EXCHANGE',
    'SAVING_CANCEL_REFUND',
    'SAVING_MATURITY',
    'INSTALLMENT_PAYMENT'
    ) NOT NULL;
