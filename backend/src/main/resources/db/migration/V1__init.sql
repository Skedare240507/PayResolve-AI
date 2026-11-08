-- ==============================================================================
-- PayResolve AI — V1 Schema Initialization (PostgreSQL)
-- ==============================================================================
-- Adheres to money safety invariants (BIGINT minor units + ISO currency code).
-- Enforces idempotent webhook ingestion and unique action keys.
-- ==============================================================================

-- 1. Webhook Events (Durable Event Inbox)
CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paypal_event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    raw_body TEXT NOT NULL,
    headers_json JSONB,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, PROCESSED, FAILED, DUPLICATE
    attempts INT NOT NULL DEFAULT 0,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    last_error TEXT
);

CREATE INDEX idx_webhook_events_inbox ON webhook_events(status, received_at DESC);
CREATE INDEX idx_webhook_events_resource ON webhook_events(resource_id);
CREATE INDEX idx_webhook_events_event_type ON webhook_events(event_type);

-- 2. Demo Shop Orders
CREATE TABLE IF NOT EXISTS shop_orders (
    id VARCHAR(100) PRIMARY KEY,
    paypal_order_id VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, PAID, REFUNDED, FAILED
    total_minor BIGINT NOT NULL,                  -- Minor units (e.g., 8450 for $84.50)
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',   -- ISO 4217
    customer_label VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shop_orders_paypal_order ON shop_orders(paypal_order_id);

-- 3. Payments
CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(100) PRIMARY KEY,
    shop_order_id VARCHAR(100) REFERENCES shop_orders(id) ON DELETE SET NULL,
    paypal_capture_id VARCHAR(100),
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL,                  -- COMPLETED, PENDING, FAILED
    captured_at TIMESTAMPTZ
);

CREATE INDEX idx_payments_shop_order ON payments(shop_order_id);
CREATE INDEX idx_payments_capture_id ON payments(paypal_capture_id);

-- 4. Refunds
CREATE TABLE IF NOT EXISTS refunds (
    id VARCHAR(100) PRIMARY KEY,
    payment_id VARCHAR(100) REFERENCES payments(id) ON DELETE SET NULL,
    paypal_refund_id VARCHAR(100),
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL,                  -- COMPLETED, PENDING, FAILED
    source VARCHAR(20) NOT NULL,                  -- PAYPAL, SHOP
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);

-- 5. Shipments
CREATE TABLE IF NOT EXISTS shipments (
    id VARCHAR(100) PRIMARY KEY,
    shop_order_id VARCHAR(100) REFERENCES shop_orders(id) ON DELETE SET NULL,
    carrier VARCHAR(100),
    tracking_ref VARCHAR(100),
    status VARCHAR(50) NOT NULL,                  -- PENDING, SHIPPED, DELIVERED
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shipments_shop_order ON shipments(shop_order_id);

-- 6. Payment Exception Cases
CREATE TABLE IF NOT EXISTS cases (
    id VARCHAR(100) PRIMARY KEY,                  -- e.g. PR-0142
    type VARCHAR(100) NOT NULL,                   -- PAID_NOT_MARKED, DUPLICATE_EVENT, AMOUNT_MISMATCH, etc.
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',   -- OPEN, INVESTIGATING, ACTION_PROPOSED, RESOLVED, DISMISSED
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', -- HIGH, MEDIUM, LOW
    paypal_order_id VARCHAR(100),
    shop_order_id VARCHAR(100) REFERENCES shop_orders(id) ON DELETE SET NULL,
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    summary TEXT
);

CREATE INDEX idx_cases_queue ON cases(status, severity, opened_at DESC);
CREATE INDEX idx_cases_type_paypal_order ON cases(type, paypal_order_id);
CREATE INDEX idx_cases_shop_order_id ON cases(shop_order_id);

-- 7. Case Evidence Items (Derived at read time for Evidence Graph)
CREATE TABLE IF NOT EXISTS case_evidence (
    id VARCHAR(100) PRIMARY KEY,
    case_id VARCHAR(100) NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    kind VARCHAR(50) NOT NULL,                    -- PAYPAL_ORDER, PAYPAL_CAPTURE, SHOP_ORDER, etc.
    source VARCHAR(20) NOT NULL,                  -- PAYPAL, SHOP, SYSTEM
    external_id VARCHAR(100),
    payload_json JSONB NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_case_evidence_case_id ON case_evidence(case_id);

-- 8. AI Investigations
CREATE TABLE IF NOT EXISTS investigations (
    id VARCHAR(100) PRIMARY KEY,
    case_id VARCHAR(100) NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,                  -- CAUSE_FOUND, CAUSE_NOT_DETERMINED, IN_PROGRESS, FAILED
    report_json JSONB,
    model VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_investigations_case_id ON investigations(case_id);

-- 9. Investigation Steps (Auditable AI Agent trace)
CREATE TABLE IF NOT EXISTS investigation_steps (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    investigation_id VARCHAR(100) NOT NULL REFERENCES investigations(id) ON DELETE CASCADE,
    tool VARCHAR(100) NOT NULL,
    args_json JSONB,
    result_digest TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ms BIGINT NOT NULL
);

CREATE INDEX idx_investigation_steps_inv_id ON investigation_steps(investigation_id);

-- 10. Proposed Actions (Human-in-the-Loop)
CREATE TABLE IF NOT EXISTS proposed_actions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id VARCHAR(100) NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    type VARCHAR(100) NOT NULL,                   -- SYNC_ORDER_STATUS, HOLD_REFUND, REQUEST_REVIEW, NONE
    payload_json JSONB,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'AWAITING_APPROVAL', -- AWAITING_APPROVAL, APPROVED, REJECTED, EXECUTED
    proposed_by VARCHAR(100) NOT NULL,            -- AI, operator
    decided_by VARCHAR(100),
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_proposed_actions_case_id ON proposed_actions(case_id);
CREATE INDEX idx_proposed_actions_status ON proposed_actions(status);

-- 11. Append-Only Audit Log
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(150) NOT NULL,
    subject_type VARCHAR(50) NOT NULL,
    subject_id VARCHAR(100) NOT NULL,
    details_json JSONB
);

CREATE INDEX idx_audit_log_at ON audit_log(at DESC);
CREATE INDEX idx_audit_log_subject ON audit_log(subject_type, subject_id);

-- 12. Authentication: Users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',   -- OPERATOR, VIEWER
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- 13. Authentication: Password Reset Tokens
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);

-- 14. Seeded Benchmark Runs
CREATE TABLE IF NOT EXISTS benchmark_runs (
    id VARCHAR(100) PRIMARY KEY,
    run_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_cases INT NOT NULL,
    passed_cases INT NOT NULL,
    precision_pct NUMERIC(5,2),
    recall_pct NUMERIC(5,2),
    avg_duration_ms BIGINT,
    results_json JSONB
);

CREATE INDEX idx_benchmark_runs_at ON benchmark_runs(run_at DESC);
