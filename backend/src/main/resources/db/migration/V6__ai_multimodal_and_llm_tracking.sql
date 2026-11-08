-- PayResolve AI: V6 AI Multi-Provider Tracking, Cache and Multimodal Attachments

-- 1. LLM Calls Tracking & Telemetry
CREATE TABLE IF NOT EXISTS llm_calls (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    case_id VARCHAR(50),
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    call_type VARCHAR(50) NOT NULL,
    prompt_tokens INT,
    completion_tokens INT,
    latency_ms BIGINT NOT NULL,
    cost_est_usd NUMERIC(10, 6) DEFAULT 0.000000,
    status VARCHAR(50) NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    fallback_reason TEXT,
    snapshot_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_llm_calls_case_id ON llm_calls(case_id);
CREATE INDEX IF NOT EXISTS idx_llm_calls_snapshot_hash ON llm_calls(snapshot_hash);
CREATE INDEX IF NOT EXISTS idx_llm_calls_created_at ON llm_calls(created_at DESC);

-- 2. Case Attachments (Multimodal Evidence)
CREATE TABLE IF NOT EXISTS case_attachments (
    id VARCHAR(50) PRIMARY KEY,
    case_id VARCHAR(50) NOT NULL REFERENCES cases(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0 AND file_size <= 4194304), -- max 4MB
    data_base64 TEXT NOT NULL,
    extracted_data JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_case_attachments_case_id ON case_attachments(case_id);

-- 3. Row Level Security Lockdown
ALTER TABLE llm_calls ENABLE ROW LEVEL SECURITY;
ALTER TABLE case_attachments ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.llm_calls, public.case_attachments FROM anon, authenticated;

