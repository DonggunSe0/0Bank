CREATE TABLE `codef_external_account_connection` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_synced_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `organization` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `connected_id_iv` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `encryption_key_version` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `connected_id_ciphertext` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','REAUTH_REQUIRED','REVOKED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_codef_connection_user_organization` (`user_id`,`organization`),
  CONSTRAINT `fk_codef_connection_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
