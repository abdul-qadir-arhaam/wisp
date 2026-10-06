-- =============================================================================
-- Migration: 20261006000001_initial_schema.sql
-- Description: Core tables, RLS policies, indexes, and Realtime publications for Wisp.
-- Specifications: PRD.md Section 6.1 & ARCHITECTURE.md Section 4
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Helper Functions
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- -----------------------------------------------------------------------------
-- 2. Table: items (Tasks & Notes Unified Data Model)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    due_at TIMESTAMPTZ,
    completed BOOLEAN NOT NULL DEFAULT false,
    completed_at TIMESTAMPTZ,
    source_device TEXT NOT NULL DEFAULT 'unknown',
    reminder_sent_day_before BOOLEAN NOT NULL DEFAULT false,
    reminder_sent_overdue BOOLEAN NOT NULL DEFAULT false
);

-- Indexes for querying and sorting performance (Priority, Oldest, Newest)
CREATE INDEX IF NOT EXISTS idx_items_user_id ON public.items(user_id);
CREATE INDEX IF NOT EXISTS idx_items_created_at ON public.items(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_items_due_at ON public.items(user_id, due_at) WHERE completed = false;
CREATE INDEX IF NOT EXISTS idx_items_completed ON public.items(user_id, completed, completed_at);

-- -----------------------------------------------------------------------------
-- 3. Table: screen_time_logs (Per-app, per-device usage tracking)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.screen_time_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device TEXT NOT NULL, -- e.g. 'android', 'windows'
    app_name TEXT NOT NULL,
    duration_seconds BIGINT NOT NULL DEFAULT 0,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    category TEXT, -- optional post-MVP
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_screen_time_user_date ON public.screen_time_logs(user_id, date);
CREATE INDEX IF NOT EXISTS idx_screen_time_device ON public.screen_time_logs(user_id, device, date);

-- -----------------------------------------------------------------------------
-- 4. Table: reels_logs (Experimental short-form scroll counter, Android-only)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.reels_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    scroll_count INTEGER NOT NULL DEFAULT 0,
    app_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reels_logs_user_date ON public.reels_logs(user_id, date);

-- -----------------------------------------------------------------------------
-- 5. Table: user_preferences (Cross-platform persistent user settings)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.user_preferences (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    selected_view TEXT NOT NULL DEFAULT 'priority' CHECK (selected_view IN ('priority', 'oldest', 'newest')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER update_user_preferences_updated_at
    BEFORE UPDATE ON public.user_preferences
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- -----------------------------------------------------------------------------
-- 6. Row-Level Security (RLS)
-- Enforces that each authenticated user can only access their own records.
-- -----------------------------------------------------------------------------
ALTER TABLE public.items ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.screen_time_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reels_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;

-- Policies for: items
CREATE POLICY "Users can view own items"
    ON public.items FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can create own items"
    ON public.items FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own items"
    ON public.items FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own items"
    ON public.items FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- Policies for: screen_time_logs
CREATE POLICY "Users can view own screen_time_logs"
    ON public.screen_time_logs FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can create own screen_time_logs"
    ON public.screen_time_logs FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own screen_time_logs"
    ON public.screen_time_logs FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own screen_time_logs"
    ON public.screen_time_logs FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- Policies for: reels_logs
CREATE POLICY "Users can view own reels_logs"
    ON public.reels_logs FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can create own reels_logs"
    ON public.reels_logs FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own reels_logs"
    ON public.reels_logs FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own reels_logs"
    ON public.reels_logs FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- Policies for: user_preferences
CREATE POLICY "Users can view own user_preferences"
    ON public.user_preferences FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can create own user_preferences"
    ON public.user_preferences FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own user_preferences"
    ON public.user_preferences FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own user_preferences"
    ON public.user_preferences FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- -----------------------------------------------------------------------------
-- 7. Supabase Realtime Setup
-- Enable real-time change broadcasting for items and user_preferences.
-- -----------------------------------------------------------------------------
ALTER PUBLICATION supabase_realtime ADD TABLE public.items;
ALTER PUBLICATION supabase_realtime ADD TABLE public.user_preferences;

-- Set REPLICA IDENTITY to FULL so updates and deletes publish full previous row data
ALTER TABLE public.items REPLICA IDENTITY FULL;
ALTER TABLE public.user_preferences REPLICA IDENTITY FULL;
