ALTER TABLE identity_verifications
    ADD COLUMN ocr_resident_number_hash VARCHAR(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER ocr_resident_number;

ALTER TABLE identity_verifications
    MODIFY COLUMN status ENUM('COMPLETED','FAILED','GOVERNMENT_VERIFIED','OCR_COMPLETED','OCR_PENDING','ONE_WON_IN_PROGRESS','ONE_WON_PENDING')
    COLLATE utf8mb4_unicode_ci NOT NULL;
