CREATE TABLE IF NOT EXISTS top_real_coins_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    count INT NOT NULL,
    symbols_json TEXT NOT NULL,
    created_at_ms BIGINT NOT NULL
);

