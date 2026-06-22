
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounts` (
  `balance` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `account_number` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_type` enum('DEPOSIT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','CLOSED','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6kplolsdtr3slnvx97xsy2kc8` (`account_number`),
  KEY `FKnjuop33mo69pd79ctplkck40n` (`user_id`),
  CONSTRAINT `FKnjuop33mo69pd79ctplkck40n` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `currencies` (
  `decimal_places` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `country_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency_code` enum('AUD','CAD','CNY','EUR','GBP','HKD','JPY','KRW','SGD','USD') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','INACTIVE','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjekn45c17p62ja9i4g7xj1st8` (`currency_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deposits` (
  `interest_rate` double NOT NULL,
  `maturity_date` date NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `expected_interest` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `principal` bigint NOT NULL,
  `saving_product_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `withdraw_account_id` bigint NOT NULL,
  `status` enum('ACTIVE','CANCELLED','MATURED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1raon6ofpkkj2ov1397o1k48k` (`saving_product_id`),
  KEY `FK6rrn8357gkkm2l4djgd4d3hke` (`user_id`),
  KEY `FKgntjio3cywdax7nalisi21ilb` (`withdraw_account_id`),
  CONSTRAINT `FK1raon6ofpkkj2ov1397o1k48k` FOREIGN KEY (`saving_product_id`) REFERENCES `saving_product` (`id`),
  CONSTRAINT `FK6rrn8357gkkm2l4djgd4d3hke` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKgntjio3cywdax7nalisi21ilb` FOREIGN KEY (`withdraw_account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_orders` (
  `applied_rate` decimal(19,6) NOT NULL,
  `fee` decimal(19,4) NOT NULL,
  `fee_rate` decimal(9,6) NOT NULL,
  `from_amount` decimal(19,4) NOT NULL,
  `to_amount` decimal(19,4) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `exchange_quote_id` bigint NOT NULL,
  `fx_wallet_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `krw_account_id` bigint DEFAULT NULL,
  `transaction_history_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `direction` enum('FOREIGN_TO_KRW','KRW_TO_FOREIGN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('CANCELED','COMPLETED','FAILED','REQUESTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhhjj9b99da2qhevl894c91hhe` (`exchange_quote_id`),
  UNIQUE KEY `UKfxrmedrayywldfjfemlgux0w7` (`transaction_history_id`),
  KEY `FKrk1ujw7ahadbx1eq77kgn0cvi` (`fx_wallet_id`),
  KEY `FK1os3hwr1br68w95j674c8pl04` (`krw_account_id`),
  KEY `FKfbiv9jjc7c705vuqsn5ap5xjc` (`user_id`),
  CONSTRAINT `FK1os3hwr1br68w95j674c8pl04` FOREIGN KEY (`krw_account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FKfbiv9jjc7c705vuqsn5ap5xjc` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKr1qerd1ssds4wyo8abr6fta13` FOREIGN KEY (`transaction_history_id`) REFERENCES `transaction_histories` (`id`),
  CONSTRAINT `FKrk1ujw7ahadbx1eq77kgn0cvi` FOREIGN KEY (`fx_wallet_id`) REFERENCES `fx_wallets` (`id`),
  CONSTRAINT `FKsqtecl0139yau9d2koowns8v2` FOREIGN KEY (`exchange_quote_id`) REFERENCES `exchange_quotes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_quotes` (
  `expected_to_amount` decimal(19,4) NOT NULL,
  `fee` decimal(19,4) NOT NULL,
  `fee_rate` decimal(9,6) NOT NULL,
  `from_amount` decimal(19,4) NOT NULL,
  `rate` decimal(19,6) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `expired_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `from_currency_code` enum('AUD','CAD','CNY','EUR','GBP','HKD','JPY','KRW','SGD','USD') COLLATE utf8mb4_unicode_ci NOT NULL,
  `to_currency_code` enum('AUD','CAD','CNY','EUR','GBP','HKD','JPY','KRW','SGD','USD') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7edj0h6emri8taw5j8bthk11l` (`from_currency_code`),
  KEY `FK49dr6jj70srspu5byoa765u7s` (`to_currency_code`),
  KEY `FK64s8adboppo9lp47vy5fu767v` (`user_id`),
  CONSTRAINT `FK49dr6jj70srspu5byoa765u7s` FOREIGN KEY (`to_currency_code`) REFERENCES `currencies` (`currency_code`),
  CONSTRAINT `FK64s8adboppo9lp47vy5fu767v` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK7edj0h6emri8taw5j8bthk11l` FOREIGN KEY (`from_currency_code`) REFERENCES `currencies` (`currency_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exchange_rates` (
  `base_price` decimal(19,6) NOT NULL,
  `currency_unit` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rate_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `currency_code` enum('AUD','CAD','CNY','EUR','GBP','HKD','JPY','KRW','SGD','USD') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm2jygybh1lsuh8sqnrb6g4n91` (`currency_code`),
  CONSTRAINT `FKt4ojang9xj79cqmed7rc0gqri` FOREIGN KEY (`currency_code`) REFERENCES `currencies` (`currency_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_account` (
  `balance` decimal(19,2) NOT NULL,
  `last_transaction_at` date DEFAULT NULL,
  `maturity_at` date DEFAULT NULL,
  `opened_at` date DEFAULT NULL,
  `withdrawable_amount` decimal(19,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `organization` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_number_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_number_masked` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_alias` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_type` enum('DEMAND','FUND','FX','INSURANCE','LOAN','SAVING','UNKNOWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','CLOSED','UNKNOWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_account_user_org_number_hash` (`user_id`,`organization`,`account_number_hash`),
  CONSTRAINT `FK537ioadmr8gxmc5wsdug55w3k` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `external_asset_transactions` (
  `amount` decimal(19,2) NOT NULL,
  `balance_after` decimal(19,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `external_account_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transacted_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `raw_category` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `counterparty_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `transaction_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `memo` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `direction` enum('IN','OUT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_asset_transaction_account_key` (`external_account_id`,`transaction_key`),
  CONSTRAINT `FKjn8k1qg869dxc59i6c19ntffp` FOREIGN KEY (`external_account_id`) REFERENCES `external_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fx_wallet_ledgers` (
  `amount` decimal(19,4) NOT NULL,
  `balance_after` decimal(19,4) NOT NULL,
  `balance_before` decimal(19,4) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `exchange_order_id` bigint DEFAULT NULL,
  `fx_wallet_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transacted_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `currency_code` enum('AUD','CAD','CNY','EUR','GBP','HKD','JPY','KRW','SGD','USD') COLLATE utf8mb4_unicode_ci NOT NULL,
  `direction` enum('IN','OUT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK28fp6hjnqi1icyietfrakpnta` (`currency_code`),
  KEY `FKat8k9jbot7n4btxxx4m42yj1j` (`exchange_order_id`),
  KEY `FKqa0fa4sutqxudkp1pkx7sxu80` (`fx_wallet_id`),
  CONSTRAINT `FK28fp6hjnqi1icyietfrakpnta` FOREIGN KEY (`currency_code`) REFERENCES `currencies` (`currency_code`),
  CONSTRAINT `FKat8k9jbot7n4btxxx4m42yj1j` FOREIGN KEY (`exchange_order_id`) REFERENCES `exchange_orders` (`id`),
  CONSTRAINT `FKqa0fa4sutqxudkp1pkx7sxu80` FOREIGN KEY (`fx_wallet_id`) REFERENCES `fx_wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fx_wallets` (
  `balance` decimal(19,4) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `currency_code` enum('AUD','CAD','CNY','EUR','GBP','HKD','JPY','KRW','SGD','USD') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','CLOSED','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fx_wallets_user_currency` (`user_id`,`currency_code`),
  KEY `FKk2o8lst5cv32l64jwvumjato6` (`currency_code`),
  CONSTRAINT `FK27atywg37rdfrkcm5qdvibx2v` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKk2o8lst5cv32l64jwvumjato6` FOREIGN KEY (`currency_code`) REFERENCES `currencies` (`currency_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `idempotency_keys` (
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_type` enum('EXCHANGE_ORDER','ONE_WON_VERIFICATION','TOPUP','TRANSFER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_body` text COLLATE utf8mb4_unicode_ci,
  `status` enum('EXPIRED','FAILED','PROCESSING','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_idempotency_key` (`user_id`,`idempotency_key`),
  CONSTRAINT `FK7rrvy13e8y772c77g5f2yw35c` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `identity_verifications` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `ocr_issue_date` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ocr_resident_number` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ocr_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `failure_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('COMPLETED','FAILED','GOVERNMENT_VERIFIED','OCR_COMPLETED','OCR_PENDING','ONE_WON_PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdvhkqf3v3puvymyb9iec8dv30` (`user_id`),
  CONSTRAINT `FKdvhkqf3v3puvymyb9iec8dv30` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `installments` (
  `auto_transfer_yn` bit(1) NOT NULL,
  `interest_rate` double NOT NULL,
  `maturity_date` date NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `monthly_amount` bigint NOT NULL,
  `paid_amount` bigint NOT NULL,
  `saving_product_id` bigint NOT NULL,
  `target_amount` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `withdraw_account_id` bigint NOT NULL,
  `status` enum('ACTIVE','CANCELLED','MATURED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3t53r4dvy8rxfnv3w98irlstk` (`saving_product_id`),
  KEY `FK9gspuqtrrbemqdrliqi8no8tn` (`user_id`),
  KEY `FKp7h23vlblmafihutd8e5yekqp` (`withdraw_account_id`),
  CONSTRAINT `FK3t53r4dvy8rxfnv3w98irlstk` FOREIGN KEY (`saving_product_id`) REFERENCES `saving_product` (`id`),
  CONSTRAINT `FK9gspuqtrrbemqdrliqi8no8tn` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKp7h23vlblmafihutd8e5yekqp` FOREIGN KEY (`withdraw_account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `investment_accounts` (
  `cash_balance` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `account_number` varchar(13) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency_code` enum('KRW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','CLOSED','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_investment_accounts_account_number` (`account_number`),
  KEY `FK20ctdbm1t8knhyk5aikdyfeg0` (`user_id`),
  CONSTRAINT `FK20ctdbm1t8knhyk5aikdyfeg0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `investment_holdings` (
  `average_price` decimal(19,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `investment_account_id` bigint NOT NULL,
  `quantity` bigint NOT NULL,
  `stock_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_investment_holdings_account_stock` (`investment_account_id`,`stock_id`),
  KEY `FK5b7rsgs5topxa54ms2oxus6tn` (`stock_id`),
  CONSTRAINT `FK5b7rsgs5topxa54ms2oxus6tn` FOREIGN KEY (`stock_id`) REFERENCES `stocks` (`id`),
  CONSTRAINT `FKcf9xmm2fxtgdi4wk7aq5iwkfu` FOREIGN KEY (`investment_account_id`) REFERENCES `investment_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `investment_order_executions` (
  `created_at` datetime(6) DEFAULT NULL,
  `execution_price` bigint NOT NULL,
  `execution_quantity` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `investment_order_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6pkek74ii3bp99rkpwd47xkhx` (`investment_order_id`),
  CONSTRAINT `FK6pkek74ii3bp99rkpwd47xkhx` FOREIGN KEY (`investment_order_id`) REFERENCES `investment_orders` (`investment_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `investment_orders` (
  `created_at` datetime(6) DEFAULT NULL,
  `investment_account_id` bigint NOT NULL,
  `investment_order_id` bigint NOT NULL AUTO_INCREMENT,
  `order_price` bigint DEFAULT NULL,
  `quantity` bigint NOT NULL,
  `remaining_quantity` bigint NOT NULL,
  `stock_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `idempotency_key` binary(16) NOT NULL,
  `price_type` enum('LIMIT','MARKET') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('CANCELLED','FILLED','PARTIALLY_FILLED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `trade_type` enum('BUY','SELL') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`investment_order_id`),
  UNIQUE KEY `uk_investment_orders_account_idempotency` (`investment_account_id`,`idempotency_key`),
  KEY `FK3jcjh27m7rj75lh82vdnw1lfe` (`stock_id`),
  CONSTRAINT `FK3jcjh27m7rj75lh82vdnw1lfe` FOREIGN KEY (`stock_id`) REFERENCES `stocks` (`id`),
  CONSTRAINT `FKi5gd376ofn9dqat8os4o7w3v7` FOREIGN KEY (`investment_account_id`) REFERENCES `investment_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `market_holidays` (
  `holiday_date` date NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `market_type` enum('KRX') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_market_holidays_market_date` (`market_type`,`holiday_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `saving_product` (
  `active` bit(1) NOT NULL,
  `interest_rate` double NOT NULL,
  `period_month` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `max_amount` bigint DEFAULT NULL,
  `min_amount` bigint NOT NULL,
  `monthly_limit` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `bank_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `bank_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `terms` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('DEPOSIT','INSTALLMENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stock_watchlists` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stock_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stock_watchlists_user_stock` (`user_id`,`stock_id`),
  KEY `FK127c8o2959bdegfyd0898yvgm` (`stock_id`),
  CONSTRAINT `FK127c8o2959bdegfyd0898yvgm` FOREIGN KEY (`stock_id`) REFERENCES `stocks` (`id`),
  CONSTRAINT `FKl52biya285s74q1k941l85fv6` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stocks` (
  `listed_date` date DEFAULT NULL,
  `capital_amount` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `market_cap` bigint DEFAULT NULL,
  `net_income` bigint DEFAULT NULL,
  `previous_volume` bigint DEFAULT NULL,
  `sales_amount` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `standard_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stock_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stock_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `currency_code` enum('KRW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `market` enum('KOSPI') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','DELISTED','SUSPENDED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocks_stock_code` (`stock_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transaction_histories` (
  `account_id` bigint NOT NULL,
  `amount` bigint NOT NULL,
  `balance_after` bigint NOT NULL,
  `balance_before` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transacted_at` datetime(6) NOT NULL,
  `transfer_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `counterparty_account_number` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `counterparty_name` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `memo` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `direction` enum('IN','OUT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('DEPOSIT','PAYMENT','TRANSFER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5jmw98fpx0ltvdmlndjtih3ta` (`account_id`),
  KEY `FKfjfxklgpl39wt2j6vggu6dtv2` (`transfer_id`),
  CONSTRAINT `FK5jmw98fpx0ltvdmlndjtih3ta` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FKfjfxklgpl39wt2j6vggu6dtv2` FOREIGN KEY (`transfer_id`) REFERENCES `transfers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transfers` (
  `amount` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiver_account_id` bigint NOT NULL,
  `sender_account_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `memo` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('FAILED','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnbxbdmgd7uwg0t1kn4tdnwdk7` (`receiver_account_id`),
  KEY `FK7lrf1tt43p7lt27jmu6dt00hl` (`sender_account_id`),
  CONSTRAINT `FK7lrf1tt43p7lt27jmu6dt00hl` FOREIGN KEY (`sender_account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FKnbxbdmgd7uwg0t1kn4tdnwdk7` FOREIGN KEY (`receiver_account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_consents` (
  `agreed` bit(1) NOT NULL,
  `agreed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `terms_type` enum('FINANCIAL_INFO','MARKETING','PERSONAL_INFO','SERVICE_TERMS') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqsufugovv6iuokr9ryulmh1sl` (`user_id`,`terms_type`),
  CONSTRAINT `FK2jrdhofjcd44quyuq5x9wlb8b` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profile_interests` (
  `profile_id` bigint NOT NULL,
  `interest` enum('FOREIGN_EXCHANGE','INSURANCE','INVESTMENT','LOAN','PENSION','SAVINGS') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKpod6mnoyyi03c49v3v5u8q3ho` (`profile_id`),
  CONSTRAINT `FKpod6mnoyyi03c49v3v5u8q3ho` FOREIGN KEY (`profile_id`) REFERENCES `user_profiles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profiles` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `region` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `age_group` enum('FIFTIES_PLUS','FORTIES','TEENS','THIRTIES','TWENTIES') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `occupation_status` enum('EMPLOYED','ETC','FREELANCER','SELF_EMPLOYED','STUDENT','UNEMPLOYED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKe5h89rk3ijvdmaiig4srogdc6` (`user_id`),
  CONSTRAINT `FKjcad5nfve11khsnpwj1mv8frj` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `birth_date` date NOT NULL,
  `identity_verified` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `phone_number` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','DORMANT','WITHDRAWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `young_policy` (
  `max_age` int DEFAULT NULL,
  `min_age` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `apply_method` text COLLATE utf8mb4_unicode_ci,
  `apply_period` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `apply_url` text COLLATE utf8mb4_unicode_ci,
  `category` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `job_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `policy_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sub_category` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4vq9qwa5a1b81snlr9484y85m` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

