# Architecture Document
## Wisp — System Architecture & Project Structure

**Version:** 1.0 (MVP Scope)
**Companion document to:** `PRD.md`

---

## 1. Architecture Overview

Wisp is a **multi-client, single-backend system**. There is no custom application server — Supabase acts as the entire backend (database, authentication, and real-time sync layer). Each platform client (Android, Windows) is a thin, independent application that talks directly to Supabase. The two clients never talk to each other directly; Supabase is always the intermediary and single source of truth.

```
┌───────────────────────┐         ┌───────────────────────┐
│     ANDROID CLIENT      │         │     WINDOWS CLIENT      │
│  (Orb + Main App)       │         │  (Tray + Popup)         │
│                         │         │                         │
│  • Voice capture        │         │  • Typed capture        │
│  • Orb UI               │         │  • Tray popup UI        │
│  • Screen-time agent    │         │  • Screen-time agent    │
│  • Reels tracker        │         │  • Local notifications  │
│  • Local notifications  │         │                         │
└───────────┬─────────────┘         └───────────┬─────────────┘
            │                                     │
            │          HTTPS / WebSocket          │
            │     (Supabase client SDK, per       │
            │      platform — Kotlin & JS/TS)     │
            │                                     │
            └──────────────────┬──────────────────┘
                                │
                   ┌────────────────────────┐
                   │        SUPABASE         │
                   │   (Backend-as-a-Service)│
                   │                         │
                   │  • Auth                 │
                   │  • Postgres Database    │
                   │  • Realtime Subscriptions│
                   │  • Edge Functions        │
                   │    (scheduled jobs)      │
                   └────────────┬────────────┘
                                │
                   ┌────────────────────────┐
                   │   PUSH DISPATCH SERVICE  │
                   │  (Firebase Cloud Messaging│
                   │   or equivalent)         │
                   │  Triggered by a scheduled │
                   │  Edge Function that scans │
                   │  due reminders            │
                   └────────────────────────┘
```

**Why this shape:** Supabase's realtime subscriptions remove the need to build a custom sync protocol — each client simply subscribes to "my own records" and receives pushed updates when another device writes a change. This is what makes the core "capture on phone, see on laptop" loop possible without a hand-built sync engine.

---

## 2. Component Breakdown

### 2.1 Android Client

| Layer | Responsibility |
|---|---|
| **Presentation — Orb** | Floating overlay window, drag/snap physics, idle/compact/expanded state rendering |
| **Presentation — Main App** | Full list view, filtering, completion UI, screen-time dashboard UI |
| **Voice Module** | Speech-to-text capture, natural-language deadline parsing, intent matching (add / complete / query / change view) |
| **Sync Client** | Supabase SDK wrapper — handles auth session, reads/writes items, subscribes to realtime changes |
| **Screen Time Agent** | `UsageStatsManager` polling, periodic upload of usage data to Supabase |
| **Reels Tracker** | Accessibility Service hook (experimental module, isolated from core logic) |
| **Notification Handler** | Receives push payloads (via FCM), displays native Android notifications |
| **Local Permissions Layer** | Manages overlay, usage-access, accessibility, and notification permissions |

### 2.2 Windows Client

| Layer | Responsibility |
|---|---|
| **Presentation — Tray Popup** | Small popup window UI, list view, filtering, completion UI (same logic as Android main app) |
| **Main Process (Electron/Tauri)** | System tray icon, window lifecycle, native OS integration (notifications, startup-on-login) |
| **Sync Client** | Supabase SDK wrapper (JS/TS) — same role as Android's sync client |
| **Screen Time Agent** | Active-window polling process, periodic upload to Supabase |
| **Notification Handler** | Receives push payloads, displays native Windows toast notifications |

### 2.3 Backend (Supabase)

| Component | Responsibility |
|---|---|
| **Auth** | Email/password + Google sign-in, issues session tokens used by both clients |
| **Postgres Database** | Source of truth for `items` (tasks/notes) and `screen_time_logs` |
| **Row-Level Security (RLS)** | Ensures each user can only read/write their own records |
| **Realtime Subscriptions** | Pushes change events (insert/update/delete) to subscribed clients |
| **Edge Function — Reminder Scanner** | Scheduled job (runs every few minutes) that scans `items` for due reminders and triggers push dispatch |
| **Edge Function — Screen Time Aggregator** (optional, post-MVP) | Pre-aggregates raw usage logs into daily/weekly summaries for faster dashboard reads |

### 2.4 Push Dispatch Service

Supabase does not send push notifications itself — it can only run scheduled server-side logic. A dedicated push service (e.g., **Firebase Cloud Messaging**, which supports both Android and Windows/desktop delivery) is required as a small additional piece:

1. Supabase Edge Function runs on a schedule, queries for items needing a reminder right now.
2. For each match, it calls the push service's API with the target device token and message payload.
3. The push service delivers the notification to the correct device.
4. The Edge Function marks the reminder as sent (`reminder_sent_day_before` / `reminder_sent_overdue`) to prevent duplicates.

This is a small but necessary addition to the architecture — worth setting up early since reminders depend on it entirely.

---

## 3. Data Flow Diagrams

### 3.1 Capture Flow (Voice, Android)

```
User speaks → Speech-to-Text → Text cleaned (filler stripped)
     → Date parser extracts due_at (if present)
     → Item object constructed { content, due_at, user_id }
     → Written to Supabase `items` table
     → Supabase Realtime broadcasts change to all subscribed clients
     → Windows client (if open) updates its list automatically
     → Android confirms via spoken reply ("Got it, saved")
```

### 3.2 Completion Flow (Voice, Android)

```
User says "mark X as done"
     → Intent parser identifies this as a completion command
     → Fuzzy-match spoken phrase against active item contents
     → If one strong match → update `completed = true`, `completed_at = now()`
     → If multiple ambiguous matches → ask one clarifying question → then update
     → Change written to Supabase → Realtime broadcasts to other devices
```

### 3.3 Reminder Flow

```
Scheduled Edge Function runs (e.g., every 5–15 minutes)
     → Query: items where due_at is within reminder window AND reminder not yet sent
     → For each match → call Push Dispatch Service with device token + message
     → Push delivered to Android (FCM) or Windows (toast via same service)
     → Edge Function marks reminder flag as sent
```

### 3.4 Screen Time Flow

```
Android: UsageStatsManager polled periodically (background service)
Windows: Active-window agent polled periodically (background process)
     → Both write usage records to Supabase `screen_time_logs`, tagged by device + user_id
     → Dashboard (on either platform) queries aggregated records across all devices
     → Displayed as unified daily/weekly breakdown
```

---

## 4. Data Model (Reference)

See `PRD.md` Section 6.1.1 for full field-level detail. Summary of core tables:

**`items`** — tasks and notes (unified type)
`id, user_id, content, created_at, due_at, completed, completed_at, source_device, reminder_sent_day_before, reminder_sent_overdue`

**`screen_time_logs`** — raw or aggregated usage records
`id, user_id, device, app_name, duration_seconds, date, category (optional, post-MVP)`

**`reels_logs`** (Android-only, experimental)
`id, user_id, date, scroll_count, app_name`

**`user_preferences`**
`user_id, selected_view (priority/oldest/newest)`

---

## 5. Cross-Cutting Concerns

| Concern | Approach |
|---|---|
| **Security** | Row-Level Security on every table, scoped to `auth.uid()`; no client ever queries another user's data, enforced at the database level, not just in app logic |
| **Offline handling** | Not supported in v1 (see PRD Section 8.4) — writes fail gracefully with a retry prompt if no connectivity |
| **Consistency** | Supabase Postgres is the single source of truth; no client maintains its own authoritative copy of the data in v1 |
| **Extensibility** | Utility commands, Maps, and other deferred features are designed to plug into the same `items`/action-log pattern without requiring a schema rewrite |

---

## 6. Monorepo / Folder Structure

A single repository containing all platforms keeps the shared schema, docs, and design assets in one place, while each platform's app remains cleanly separated. Exact internal folder names within each app are a developer preference — the structure below is meant to convey the **layers**, not be followed exactly file-for-file.

```
wisp/
│
├── docs/                          # All planning & reference documents
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   └── design/                    # Exported UI/UX assets, Stitch outputs, etc.
│
├── supabase/                      # Backend — database, auth config, scheduled jobs
│   ├── migrations/                # SQL migration files (table creation, RLS policies)
│   ├── functions/                 # Edge Functions
│   │   ├── reminder-scanner/      # Scheduled job — checks due items, triggers push
│   │   └── screen-time-aggregator/ # (post-MVP) summarizes raw usage logs
│   └── config.toml                # Supabase project config
│
├── android/                       # Native Android app
│   ├── app/
│   │   ├── orb/                   # Floating overlay — states, drag/snap, service
│   │   ├── mainapp/                # Full list view, filtering, completion UI
│   │   ├── voice/                  # Speech-to-text, date parsing, intent matching
│   │   ├── screentime/             # UsageStatsManager integration
│   │   ├── reels/                  # Accessibility Service module (experimental)
│   │   ├── sync/                   # Supabase client wrapper, realtime subscriptions
│   │   ├── notifications/          # Push + local notification handling
│   │   └── auth/                   # Login/logout screens, session handling
│   └── build.gradle
│
├── windows/                       # Desktop companion app (Electron or Tauri)
│   ├── src/
│   │   ├── main/                   # Main process — tray icon, window management, startup
│   │   ├── renderer/                # Popup UI — list view, filtering, completion
│   │   ├── screentime/              # Active-window tracking agent
│   │   ├── sync/                    # Supabase client wrapper (JS/TS)
│   │   ├── notifications/           # Toast notification handling
│   │   └── auth/                    # Login/logout UI, session handling
│   └── package.json
│
├── shared/                        # Cross-platform constants & types (optional, grows over time)
│   ├── types/                      # Shared data model definitions (e.g., Item schema as TS/Kotlin-friendly spec)
│   └── constants/                  # Shared enums (view types, reminder windows, etc.)
│
└── README.md                      # Project overview, setup instructions, links to docs/
```

### 6.1 Why this structure

- **`docs/`** keeps planning artifacts versioned alongside the code, so the PRD and architecture stay close to what's actually being built, not lost in chat history.
- **`supabase/`** is treated as its own top-level layer, not buried inside either client, because it's genuinely shared infrastructure both platforms depend on equally.
- **`android/` and `windows/`** are fully independent — neither imports code from the other — reflecting the reality that their UI layers cannot be shared, even though their data model and backend are identical.
- **`shared/`** is intentionally small and optional at MVP stage — it exists so that if you find yourself duplicating the same constants (e.g., the list of valid view-sort types) across both clients, there's an obvious place to consolidate them, without forcing premature abstraction before you know what's actually worth sharing.

---

## 7. Build Order Alignment

This structure is designed to be built in the same order as the recommended sequence in `PRD.md` Section 12:

1. `supabase/` — schema + RLS + auth, tested independently first
2. `android/app/sync` + `android/app/voice` — prove the core capture → sync loop before any UI polish
3. `android/app/orb` and `android/app/mainapp` — UI layers
4. `supabase/functions/reminder-scanner` + `android/app/notifications` — reminders
5. `android/app/screentime`, then `android/app/reels`
6. `windows/` — built last, reusing the same backend and data model already proven on Android

---

*End of Document*