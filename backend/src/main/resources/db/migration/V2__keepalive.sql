-- ==============================================================================
-- PayResolve AI — V2 Supabase Keep-Alive Schema
-- ==============================================================================
-- Table pinged periodically by automated workflow to prevent Supabase free tier
-- projects from pausing after 7 days of inactivity.
-- ==============================================================================

CREATE TABLE IF NOT EXISTS public.keepalive (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pinged_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Enable Row Level Security (RLS)
ALTER TABLE public.keepalive ENABLE ROW LEVEL SECURITY;

-- Conditionally ensure standard roles exist for local/Testcontainers environments
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'anon') THEN
        CREATE ROLE anon NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'authenticated') THEN
        CREATE ROLE authenticated NOLOGIN;
    END IF;
END $$;

-- Allow anon and authenticated roles to select and insert ping records
CREATE POLICY "Allow anon select and insert" ON public.keepalive
    FOR ALL
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- Grant privileges for Supabase PostgREST API access
GRANT SELECT, INSERT ON public.keepalive TO anon, authenticated;
