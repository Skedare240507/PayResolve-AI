-- PayResolve AI: V8 Rejected Webhooks, Arrival Race & Duplicate Retry Cleanup

-- 1. Separate quarantine table for unverified/rejected webhook attempts (never pollutes main inbox)
CREATE TABLE IF NOT EXISTS rejected_webhooks (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paypal_event_id VARCHAR(100),
    reason VARCHAR(255) NOT NULL,
    body_sha256 VARCHAR(64) NOT NULL,
    ip VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_rejected_webhooks_received_at ON rejected_webhooks(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_rejected_webhooks_event_id ON rejected_webhooks(paypal_event_id);

ALTER TABLE rejected_webhooks ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.rejected_webhooks FROM anon, authenticated;

-- 2. Arrival-race scheduling & payload mismatch tracking on webhook_events
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS payload_mismatch BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_webhook_events_custom_id ON webhook_events(custom_id);
CREATE INDEX IF NOT EXISTS idx_webhook_events_paypal_order ON webhook_events(paypal_order_id);

-- 3. Auto-resolve historical false-positive DUPLICATE_EVENT cases created only by processing retries (no deletes)
UPDATE cases
SET status = 'RESOLVED',
    resolved_at = now(),
    summary = summary || ' [auto-resolved: processing retry, not a duplicate delivery]'
WHERE type = 'DUPLICATE_EVENT'
  AND summary LIKE 'Duplicate delivery or repeated attempt detected%'
  AND status <> 'RESOLVED';

