-- V10: Modify YoungPolicy columns to TEXT to handle long external API inputs
ALTER TABLE `young_policy` MODIFY COLUMN `region_code` TEXT;
ALTER TABLE `young_policy` MODIFY COLUMN `job_code` TEXT;
ALTER TABLE `young_policy` MODIFY COLUMN `apply_period` TEXT;
