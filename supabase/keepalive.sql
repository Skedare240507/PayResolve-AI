-- ==============================================================================
-- PayResolve AI — Supabase Keep-Alive SQL (Run in Supabase SQL Editor)
-- ==============================================================================
-- Run this in the Supabase SQL Editor if you are initializing keep-alive on
-- an existing Supabase project directly without Flyway.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.keepalive (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pinged_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.keepalive ENABLE ROW LEVEL SECURITY;

-- Allow anon and authenticated roles to select and insert ping records
CREATE POLICY "Allow anon select and insert" ON public.keepalive
    FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- Grant privileges for Supabase PostgREST API access
GRANT SELECT, INSERT ON public.keepalive TO anon, authenticated;
