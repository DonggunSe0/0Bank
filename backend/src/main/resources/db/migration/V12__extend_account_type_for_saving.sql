ALTER TABLE accounts
    MODIFY COLUMN account_type enum('DEPOSIT', 'SAVING_DEPOSIT', 'SAVING_INSTALLMENT') NOT NULL;

ALTER TABLE deposits
    ADD COLUMN saving_account_id bigint NOT NULL;

ALTER TABLE deposits
    ADD CONSTRAINT fk_deposits_saving_account
        FOREIGN KEY (saving_account_id) REFERENCES accounts (id);

ALTER TABLE deposits
    ADD CONSTRAINT uk_deposits_saving_account_id UNIQUE (saving_account_id);

ALTER TABLE installments
    ADD COLUMN saving_account_id bigint NOT NULL;

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
