# Wisp Supabase Backend Setup (Phase 1)

This directory contains the database schema, migration scripts, and configuration for Wisp's backend.

## 1. Apply Database Migrations

You can apply the migrations in one of two ways:

### Option A: Via the Supabase Dashboard SQL Editor (Recommended & Quickest)
1. Go to your [Supabase Dashboard](https://supabase.com/dashboard) and select your project.
2. Navigate to **SQL Editor** in the left sidebar.
3. Click **New query**.
4. Copy the entire contents of [`supabase/migrations/20261006000001_initial_schema.sql`](migrations/20261006000001_initial_schema.sql) and paste it into the editor.
5. Click **Run**.

### Option B: Via Supabase CLI
```bash
cmd /c "npx supabase link --project-ref <your-project-ref>"
cmd /c "npx supabase db push"
```

---

## 2. Configure Authentication

In your Supabase project dashboard:
1. Navigate to **Authentication** -> **Providers**:
   - **Email**: Ensure Email provider is enabled. (Optional for local testing: in **Authentication -> Email Templates/Settings**, you can disable *Confirm email* to allow immediate sign-ins without email verification).
   - **Google**: Enable Google sign-in by providing your Google OAuth Client ID and Client Secret (per `PRD.md` Section 8.1).

---

## 3. Verify Realtime & RLS

The migration script automatically:
- Enables **Row-Level Security (RLS)** on `items`, `screen_time_logs`, `reels_logs`, and `user_preferences`.
- Defines strict policies restricting CRUD operations exclusively to the authenticated owner (`auth.uid() = user_id`).
- Adds `items` and `user_preferences` to the `supabase_realtime` publication with `REPLICA IDENTITY FULL`.

---

## 4. Run Automated Phase 1 Verification

1. Copy `.env.example` to `.env` in the project root:
   ```env
   SUPABASE_URL=https://your-project-id.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   ```
2. Run the automated verification test:
   ```bash
   npm run test:backend
   ```
   This will test user sign-up, item creation, item querying, RLS isolation between users, and realtime event subscriptions.
