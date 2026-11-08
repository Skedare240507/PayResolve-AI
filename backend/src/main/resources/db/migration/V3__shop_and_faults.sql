-- ==============================================================================
-- PayResolve AI — V3 Demo Shop Products and Database-Backed Fault Switches
-- ==============================================================================

-- 1. Demo Products
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed 3 standard demo products
INSERT INTO products (id, name, price_minor, currency, description)
VALUES 
    ('mug', 'Ceramic mug', 2400, 'USD', 'Handcrafted ceramic mug (12 oz)'),
    ('tote', 'Canvas tote', 3850, 'USD', 'Heavyweight 100% organic cotton canvas tote bag'),
    ('notebook', 'Notebook set', 2200, 'USD', 'Pack of 3 dot-grid softcover pocket notebooks')
ON CONFLICT (id) DO NOTHING;

-- 2. Demo Fault Switches
CREATE TABLE IF NOT EXISTS demo_fault_switches (
    id VARCHAR(50) PRIMARY KEY,
    fail_webhook_handler BOOLEAN NOT NULL DEFAULT FALSE,
    drop_webhook BOOLEAN NOT NULL DEFAULT FALSE,
    alter_order_total BOOLEAN NOT NULL DEFAULT FALSE,
    duplicate_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed DEFAULT fault switches row
INSERT INTO demo_fault_switches (id, fail_webhook_handler, drop_webhook, alter_order_total, duplicate_delivery)
VALUES ('DEFAULT', FALSE, FALSE, FALSE, FALSE)
ON CONFLICT (id) DO NOTHING;
