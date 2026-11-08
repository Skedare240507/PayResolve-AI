-- PayResolve AI: V5 Hardening. Safe whether or not V1 to V4 were already applied.

-- 1. New structures
CREATE TABLE IF NOT EXISTS shop_order_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_order_id VARCHAR(100) NOT NULL REFERENCES shop_orders(id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price_minor BIGINT NOT NULL CHECK (unit_price_minor >= 0)
);
CREATE INDEX IF NOT EXISTS idx_shop_order_items_order ON shop_order_items(shop_order_id);
CREATE SEQUENCE IF NOT EXISTS case_number_seq START 1;

-- 2. Webhook delivery counting
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS delivery_count INT NOT NULL DEFAULT 1;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS last_received_at TIMESTAMPTZ;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS event_created_at TIMESTAMPTZ;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS custom_id VARCHAR(100);
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS paypal_order_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_webhook_events_custom_id ON webhook_events(custom_id);
CREATE INDEX IF NOT EXISTS idx_webhook_events_paypal_order ON webhook_events(paypal_order_id);

-- 3. Redundant indexes
DROP INDEX IF EXISTS idx_users_username;
DROP INDEX IF EXISTS idx_users_email;
DROP INDEX IF EXISTS idx_cases_type_paypal_order;

-- 4. Money safety
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_shop_orders_total') THEN
        ALTER TABLE shop_orders ADD CONSTRAINT chk_shop_orders_total CHECK (total_minor >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_payments_amount') THEN
        ALTER TABLE payments ADD CONSTRAINT chk_payments_amount CHECK (amount_minor >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_refunds_amount') THEN
        ALTER TABLE refunds ADD CONSTRAINT chk_refunds_amount CHECK (amount_minor >= 0);
    END IF;
END $$;

-- 5. Append-only audit log
CREATE OR REPLACE FUNCTION audit_log_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$;
DROP TRIGGER IF EXISTS trg_audit_log_immutable ON audit_log;
CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

-- 6. Lock the Supabase REST API: RLS on, no policies. The backend connects as the database
-- owner (postgres.<project-ref>), which bypasses RLS.
ALTER TABLE webhook_events          ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop_orders             ENABLE ROW LEVEL SECURITY;
ALTER TABLE shop_order_items        ENABLE ROW LEVEL SECURITY;
ALTER TABLE payments                ENABLE ROW LEVEL SECURITY;
ALTER TABLE refunds                 ENABLE ROW LEVEL SECURITY;
ALTER TABLE shipments               ENABLE ROW LEVEL SECURITY;
ALTER TABLE cases                   ENABLE ROW LEVEL SECURITY;
ALTER TABLE case_evidence           ENABLE ROW LEVEL SECURITY;
ALTER TABLE investigations          ENABLE ROW LEVEL SECURITY;
ALTER TABLE investigation_steps     ENABLE ROW LEVEL SECURITY;
ALTER TABLE proposed_actions        ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log               ENABLE ROW LEVEL SECURITY;
ALTER TABLE users                   ENABLE ROW LEVEL SECURITY;
ALTER TABLE password_reset_tokens   ENABLE ROW LEVEL SECURITY;
ALTER TABLE benchmark_runs          ENABLE ROW LEVEL SECURITY;
ALTER TABLE products                ENABLE ROW LEVEL SECURITY;
ALTER TABLE demo_fault_switches     ENABLE ROW LEVEL SECURITY;
ALTER TABLE flyway_schema_history   ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM anon, authenticated;
GRANT SELECT, INSERT ON public.keepalive TO anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM anon, authenticated;

