-- V4__cases_enhancements.sql
-- Enhancements for payment exception cases: minor units amount, currency, updated_at timestamp,
-- and unique index on (type, paypal_order_id) to ensure reruns update rather than duplicate.

ALTER TABLE cases ADD COLUMN IF NOT EXISTS amount_minor BIGINT;
ALTER TABLE cases ADD COLUMN IF NOT EXISTS currency VARCHAR(3) DEFAULT 'USD';
ALTER TABLE cases ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- Unique constraint ensuring one case per (type, paypal_order_id)
CREATE UNIQUE INDEX IF NOT EXISTS uq_cases_type_paypal_order ON cases(type, paypal_order_id);
