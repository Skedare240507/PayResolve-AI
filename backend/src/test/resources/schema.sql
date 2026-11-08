-- Schema for H2 embedded database tests (PostgreSQL mode)

CREATE TABLE IF NOT EXISTS webhook_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paypal_event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    raw_body TEXT NOT NULL,
    headers_json TEXT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    attempts INT NOT NULL DEFAULT 0,
    delivery_count INT NOT NULL DEFAULT 1,
    received_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_received_at TIMESTAMP WITH TIME ZONE,
    event_created_at TIMESTAMP WITH TIME ZONE,
    custom_id VARCHAR(100),
    paypal_order_id VARCHAR(100),
    last_error TEXT
);

CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_minor BIGINT NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD' NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

MERGE INTO products (id, name, price_minor, currency, description) KEY(id)
VALUES ('mug', 'Ceramic mug', 2400, 'USD', 'Handcrafted ceramic mug (12 oz)');

MERGE INTO products (id, name, price_minor, currency, description) KEY(id)
VALUES ('tote', 'Canvas tote', 3850, 'USD', 'Heavyweight 100% organic cotton canvas tote bag');

MERGE INTO products (id, name, price_minor, currency, description) KEY(id)
VALUES ('notebook', 'Notebook set', 2200, 'USD', 'Pack of 3 dot-grid softcover pocket notebooks');

CREATE TABLE IF NOT EXISTS shop_orders (
    id VARCHAR(100) PRIMARY KEY,
    paypal_order_id VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    customer_label VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS shop_order_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shop_order_id VARCHAR(100) NOT NULL REFERENCES shop_orders(id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL REFERENCES products(id),
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price_minor BIGINT NOT NULL CHECK (unit_price_minor >= 0)
);

CREATE SEQUENCE IF NOT EXISTS case_number_seq START WITH 1;

CREATE TABLE IF NOT EXISTS payments (
    id VARCHAR(100) PRIMARY KEY,
    shop_order_id VARCHAR(100),
    paypal_capture_id VARCHAR(100),
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS refunds (
    id VARCHAR(100) PRIMARY KEY,
    payment_id VARCHAR(100),
    paypal_refund_id VARCHAR(100),
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(50) NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS shipments (
    id VARCHAR(100) PRIMARY KEY,
    shop_order_id VARCHAR(100),
    carrier VARCHAR(100),
    tracking_ref VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS cases (
    id VARCHAR(100) PRIMARY KEY,
    type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    paypal_order_id VARCHAR(100),
    shop_order_id VARCHAR(100),
    amount_minor BIGINT,
    currency VARCHAR(3) DEFAULT 'USD',
    opened_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE,
    summary TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cases_type_paypal_order ON cases(type, paypal_order_id);

CREATE TABLE IF NOT EXISTS case_evidence (
    id VARCHAR(100) PRIMARY KEY,
    case_id VARCHAR(100) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    source VARCHAR(20) NOT NULL,
    external_id VARCHAR(100),
    payload_json TEXT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS investigations (
    id VARCHAR(100) PRIMARY KEY,
    case_id VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    report_json TEXT,
    model VARCHAR(100) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS investigation_steps (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    investigation_id VARCHAR(100) NOT NULL,
    tool VARCHAR(100) NOT NULL,
    args_json TEXT,
    result_digest TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS proposed_actions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id VARCHAR(100) NOT NULL,
    type VARCHAR(100) NOT NULL,
    payload_json TEXT,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL DEFAULT 'AWAITING_APPROVAL',
    proposed_by VARCHAR(100) NOT NULL,
    decided_by VARCHAR(100),
    decided_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    actor VARCHAR(100) NOT NULL,
    action VARCHAR(150) NOT NULL,
    subject_type VARCHAR(50) NOT NULL,
    subject_id VARCHAR(100) NOT NULL,
    details_json TEXT
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS benchmark_runs (
    id VARCHAR(100) PRIMARY KEY,
    run_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total_cases INT NOT NULL,
    passed_cases INT NOT NULL,
    precision_pct DOUBLE PRECISION NOT NULL,
    recall_pct DOUBLE PRECISION NOT NULL,
    avg_duration_ms BIGINT NOT NULL,
    results_json TEXT
);

CREATE TABLE IF NOT EXISTS keepalive (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pinged_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS demo_fault_switches (
    id VARCHAR(50) PRIMARY KEY,
    fail_webhook_handler BOOLEAN DEFAULT FALSE NOT NULL,
    drop_webhook BOOLEAN DEFAULT FALSE NOT NULL,
    alter_order_total BOOLEAN DEFAULT FALSE NOT NULL,
    duplicate_delivery BOOLEAN DEFAULT FALSE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

MERGE INTO demo_fault_switches (id, fail_webhook_handler, drop_webhook, alter_order_total, duplicate_delivery) KEY(id)
VALUES ('DEFAULT', FALSE, FALSE, FALSE, FALSE);
