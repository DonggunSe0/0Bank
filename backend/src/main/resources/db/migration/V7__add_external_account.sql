CREATE TABLE IF NOT EXISTS `codef_external_account_connection` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `organization` varchar(20) NOT NULL,
  `connected_id_ciphertext` varchar(512) NOT NULL,
  `connected_id_iv` varchar(32) NOT NULL,
  `encryption_key_version` varchar(50) NOT NULL,
  `status` varchar(30) NOT NULL,
  `last_synced_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uk_codef_connection_user_organization` UNIQUE (`user_id`, `organization`),
  CONSTRAINT `fk_codef_connection_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
