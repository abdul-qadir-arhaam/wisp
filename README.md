# Wisp 🌌

> **"Speak a thought once. It's there, correctly prioritized, wherever you open Wisp next."**

Wisp is a voice-first personal assistant system designed to eliminate the loss of fleeting thoughts, tasks, and ideas that occur when you are away from your primary work device.

- **Android (Primary):** Persistent floating orb UI (Idle, Compact/Listening, Expanded) with voice capture, natural language deadline parsing, push-to-talk, screen-time tracking, and experimental reels scroll counter.
- **Windows (Companion):** Lightweight system tray companion with click-to-open popup window for typed task capture, list management, screen-time agent, and toast notifications.
- **Backend:** Centralized Supabase (Auth, PostgreSQL, Realtime Subscriptions, Edge Functions) acting as the single source of truth across all devices.

---

## 📚 Project Documentation

The binding specifications and architecture for Wisp are located in the [`docs/`](file:///c:/Users/Nasir%20Sada/Desktop/wispassistant/docs) directory:

1. **[Product Requirements Document (PRD)](docs/PRD.md):** Feature behavior, data model, and explicit non-goals.
2. **[Architecture Document](docs/ARCHITECTURE.md):** System architecture, data flow diagrams, data model, and monorepo structure.
3. **[Design Document](docs/DESIGN.md):** Visual theme (glassmorphism), dark mode color palette, orb interaction states, and screen specifications.
4. **[Phases & Execution Plan](docs/PHASES.md):** Sequential, dependency-ordered phased plan (Phases 0 through 10).

---

## 🗂️ Monorepo Structure

```
wisp/
│
├── docs/                          # All planning & reference documents
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── DESIGN.md
│   ├── PHASES.md
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
│   │   ├── mainapp/               # Full list view, filtering, completion UI
│   │   ├── voice/                 # Speech-to-text, date parsing, intent matching
│   │   ├── screentime/            # UsageStatsManager integration
│   │   ├── reels/                 # Accessibility Service module (experimental)
│   │   ├── sync/                  # Supabase client wrapper, realtime subscriptions
│   │   ├── notifications/         # Push + local notification handling
│   │   └── auth/                  # Login/logout screens, session handling
│   └── build.gradle
│
├── windows/                       # Desktop companion app (Electron or Tauri)
│   ├── src/
│   │   ├── main/                  # Main process — tray icon, window management, startup
│   │   ├── renderer/              # Popup UI — list view, filtering, completion
│   │   ├── screentime/            # Active-window tracking agent
│   │   ├── sync/                  # Supabase client wrapper (JS/TS)
│   │   ├── notifications/         # Toast notification handling
│   │   └── auth/                  # Login/logout UI, session handling
│   └── package.json
│
├── shared/                        # Cross-platform constants & types
│   ├── types/                     # Shared data model definitions (TypeScript/Kotlin)
│   └── constants/                 # Shared enums (view types, reminder windows, etc.)
│
├── .env.example                   # Environment variable template
├── .gitignore                     # Monorepo gitignore rules
└── README.md                      # Project overview
```

---

## 🚀 Getting Started

### Prerequisites
- Node.js (v18+)
- Android Studio / Android SDK (for Android app development)
- Supabase account & Supabase CLI

### Setup
1. Copy `.env.example` to `.env.local` or `.env` and fill in your Supabase project credentials.
2. Follow the phase-by-phase implementation plan outlined in [`docs/PHASES.md`](docs/PHASES.md).
