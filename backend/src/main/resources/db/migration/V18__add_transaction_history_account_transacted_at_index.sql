CREATE INDEX idx_transaction_histories_account_transacted_at
    ON transaction_histories (account_id, transacted_at DESC);