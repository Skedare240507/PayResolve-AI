-- ==============================================================================
-- PayResolve AI — V7 Order Trace Timestamps
-- ==============================================================================
-- Adds dedicated lifecycle timestamps and capture ID to shop_orders so the
-- payment trace timeline preserves every milestone without overwriting earlier steps.
-- ==============================================================================

ALTER TABLE shop_orders ADD COLUMN IF NOT EXISTS paypal_order_created_at TIMESTAMPTZ;
ALTER TABLE shop_orders ADD COLUMN IF NOT EXISTS buyer_approved_at TIMESTAMPTZ;
ALTER TABLE shop_orders ADD COLUMN IF NOT EXISTS captured_at TIMESTAMPTZ;
ALTER TABLE shop_orders ADD COLUMN IF NOT EXISTS paypal_capture_id VARCHAR(100);
ALTER TABLE shop_orders ADD COLUMN IF NOT EXISTS paid_at TIMESTAMPTZ;

