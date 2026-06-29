CREATE INDEX idx_identity_verifications_user_id_status
    ON identity_verifications (user_id, status);
