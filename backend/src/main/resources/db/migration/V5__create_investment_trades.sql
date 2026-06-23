ALTER TABLE `idempotency_keys`
    MODIFY COLUMN `operation_type` enum('EXCHANGE_ORDER','INVESTMENT_MARKET_ORDER','ONE_WON_VERIFICATION','TOPUP','TRANSFER')
    COLLATE utf8mb4_unicode_ci NOT NULL;

CREATE TABLE `investment_trades` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `investment_account_id` bigint NOT NULL,
    `stock_id` bigint NOT NULL,
    `trade_type` enum('BUY','SELL') COLLATE utf8mb4_unicode_ci NOT NULL,
    `quantity` bigint NOT NULL,
    `execution_price` bigint NOT NULL,
    `total_amount` bigint NOT NULL,
    `requested_price` bigint NOT NULL,
    `price_deviation_bps` int NOT NULL,
    `snapshot_at` datetime(6) NOT NULL,
    `idempotency_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
    `executed_at` datetime(6) NOT NULL,
    `created_at` datetime(6) DEFAULT NULL,
    `updated_at` datetime(6) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_investment_trades_account_idempotency` (`investment_account_id`, `idempotency_key`),
    KEY `idx_investment_trades_account_executed_at` (`investment_account_id`, `executed_at`),
    KEY `idx_investment_trades_stock_executed_at` (`stock_id`, `executed_at`),
    CONSTRAINT `fk_investment_trades_account`
        FOREIGN KEY (`investment_account_id`) REFERENCES `investment_accounts` (`id`),
    CONSTRAINT `fk_investment_trades_stock`
        FOREIGN KEY (`stock_id`) REFERENCES `stocks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
