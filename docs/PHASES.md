# Execution Phases
## Wisp — Phased Build Plan

**Version:** 1.0 (MVP Scope)
**Companion document to:** `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`
**Purpose:** A sequential, dependency-ordered breakdown of the build, structured so each phase is a self-contained, verifiable unit of work. Each phase lists its goal, concrete tasks, deliverables, and acceptance criteria, so progress can be checked objectively before moving to the next phase — intended to be executed incrementally, phase by phase, rather than attempted all at once.

---

## How to Use This Document

- Phases are ordered by **dependency**, not just logical grouping — a later phase assumes everything in earlier phases is complete and working.
- Each phase has an explicit **"Definition of Done"** — do not proceed to the next phase until these criteria are met, even if it's tempting to jump ahead to a more visible/exciting feature.
- Phases are intentionally scoped small enough to be tackled as a single focused work session each — if a phase feels too large while working through it, it's reasonable to split it further, but the order should be preserved.
- Referenced documents (`PRD.md`, `ARCHITECTURE.md`, `DESIGN.md`) contain the full detail behind each task; this document sequences the work, it doesn't restate every specification.

---

## Phase 0 — Project Setup & Foundations

**Goal:** A clean, version-controlled project skeleton with the backend reachable, before any feature work begins.

**Tasks:**
- Initialize the monorepo with the folder structure defined in `ARCHITECTURE.md` Section 6 (`supabase/`, `android/`, `windows/`, `shared/`, `docs/`)
- Place `PRD.md`, `ARCHITECTURE.md`, `DESIGN.md` into `docs/`
- Create a new Supabase project
- Set up version control (Git), initial commit with the empty structure
- Confirm Supabase project dashboard is reachable and API keys are available for later client setup

**Deliverables:**
- Repository with correct folder scaffolding
- Working Supabase project (empty, no schema yet)

**Definition of Done:**
- Repo pushed to remote (e.g., GitHub)
- Supabase project credentials saved securely (not committed to source control)

---

## Phase 1 — Backend Foundation (Supabase)

**Goal:** The complete data layer exists, is secure, and can be read/written independently of any client app.

**Tasks:**
- Create database tables: `items`, `screen_time_logs`, `reels_logs`, `user_preferences` (full schema in `ARCHITECTURE.md` Section 4)
- Write and apply migrations under `supabase/migrations/`
- Enable Row-Level Security on all tables, scoped to `auth.uid()`
- Configure Supabase Auth — enable email/password and Google sign-in providers
- Manually test: create a test user, insert a test `items` row via the Supabase dashboard/SQL editor, confirm RLS blocks access from a different test user
- Enable Realtime on the `items` table

**Deliverables:**
- Full schema committed under `supabase/migrations/`
- RLS policies verified working
- Auth providers configured and testable

**Definition of Done:**
- A test user can be created, can insert/read their own `items` rows, and cannot read another user's rows
- Realtime change events are observable (e.g., via Supabase's dashboard realtime inspector) when a row is inserted

---

## Phase 2 — Android: Core Capture → Sync Loop (No UI Polish)

**Goal:** Prove the single most important technical risk in the entire project — voice capture, parsing, and cross-session sync — before investing in any visual design.

**Tasks:**
- Scaffold a minimal Android app (no orb yet, a bare test screen is fine)
- Integrate Supabase Android SDK, implement login (email/password is sufficient for this phase)
- Integrate Android's `SpeechRecognizer` for basic speech-to-text
- Integrate a natural-language date-parsing library; implement filler-word stripping
- On voice input: parse text → extract `due_at` if present → write to `items` table
- Implement a basic realtime listener that logs incoming changes to the console/log output
- Manually test: log in with the same account on two separate app instances/emulators, speak a task on one, confirm the change appears via the realtime listener on the other within a few seconds

**Deliverables:**
- A working (unstyled) Android build that can capture, parse, and sync a task

**Definition of Done:**
- A spoken sentence with a natural-language deadline ("remind me to call mom tomorrow") results in a correctly parsed `due_at` in the database
- The same item is observable via realtime on a second logged-in session within a few seconds

*This is the highest-risk phase in the whole project — do not proceed until this works reliably.*

---

## Phase 3 — Android: Orb UI & State Machine

**Goal:** Build the orb's visual presence and interaction states, now that the underlying capture/sync logic is proven.

**Tasks:**
- Implement the floating overlay window using Android's overlay permission (`SYSTEM_ALERT_WINDOW`)
- Implement drag behavior and snap-to-nearest-edge-on-release physics
- Implement per-device position memory (local storage, not synced)
- Build the three orb states per `DESIGN.md` Section 6: idle, compact/listening, expanded
- Wire the previously-built voice capture (Phase 2) into the orb's mic interaction
- Implement auto-hide behavior during fullscreen apps
- Apply the glassmorphism visual treatment to the expanded state panel (per `DESIGN.md` Section 2)

**Deliverables:**
- A fully interactive floating orb with working drag/snap and all three states

**Definition of Done:**
- Orb can be dragged anywhere and snaps to the nearest edge on release
- Orb correctly transitions idle → compact → expanded based on user interaction
- Voice capture triggered from the orb successfully creates a synced item (reusing Phase 2 logic)
- Orb auto-hides in at least one tested fullscreen scenario

---

## Phase 4 — Android: Main App (Full Management View)

**Goal:** Build the full-featured list management experience.

**Tasks:**
- Build the full list view UI per `DESIGN.md` Section 7.4
- Implement the three view/filter modes (Priority, Oldest First, Newest First) and persist the selected view to `user_preferences`
- Implement tickbox completion with double-click-confirm interaction (visual fill-on-first-click, confirm-on-second-click, reset-on-timeout)
- Implement voice-based completion (fuzzy match against item content; single clarifying question only on ambiguous match)
- Implement undo/"mark as not done"
- Implement the hybrid completed-task display (struck-through in main list for today/yesterday, full history in a separate "Completed" tab)
- Implement the typed-input fallback for capture
- Build the login screen and account icon/logout flow

**Deliverables:**
- A fully functional Android main app, feature-complete per the PRD

**Definition of Done:**
- All three view filters correctly reorder the list and persist across app restarts
- Completion works correctly via both tick and voice, including the ambiguous-match clarification case
- Completed items correctly move from the main list to the Completed tab after the today/yesterday window passes
- Login/logout flow works end-to-end

---

## Phase 5 — Reminders (Backend + Android Delivery)

**Goal:** Automatic, correctly-timed push reminders for every dated task.

**Tasks:**
- Set up Firebase Cloud Messaging (or equivalent) project, obtain credentials for push dispatch
- Build the Supabase Edge Function (`reminder-scanner`) per `ARCHITECTURE.md` Section 2.3 — scheduled scan for due reminders
- Implement the day-before / same-day-fallback / one-time-overdue logic in the Edge Function
- Implement reminder-sent flags to prevent duplicate notifications
- Implement Android-side push notification receipt and display (FCM integration)
- Manually test all three reminder scenarios: a task due tomorrow, a task created today due today, and a task that becomes overdue

**Deliverables:**
- Working scheduled reminder system, end to end

**Definition of Done:**
- All three reminder scenarios fire at the correct time with the correct message, with no duplicates, verified over at least one real test cycle

---

## Phase 6 — Android: Screen Time Tracking & Analytics

**Goal:** Per-app usage tracking and a unified dashboard.

**Tasks:**
- Implement `UsageStatsManager` integration, request usage-access permission
- Build a background service to periodically upload usage data to `screen_time_logs`
- Build the Screen Time Analytics dashboard UI per `DESIGN.md` Section 7.5 (total today, breakdown chart, top apps list)
- Wire the dashboard to query and aggregate data across all devices tied to the user

**Deliverables:**
- Working screen-time tracking and dashboard on Android

**Definition of Done:**
- Usage data for at least 3 different apps is correctly logged and visible in the dashboard, with accurate per-app durations

---

## Phase 7 — Android: Reels Counter (Experimental)

**Goal:** Best-effort short-form scroll tracking, clearly isolated from core functionality.

**Tasks:**
- Implement an Accessibility Service module, scoped to detect scroll events within supported apps (start with one app, e.g., Instagram, as a proof of concept)
- Write detected scroll counts to `reels_logs`
- Add the experimental section to the Screen Time dashboard, clearly labeled, per `DESIGN.md` Section 7.5

**Deliverables:**
- A working, isolated reels-tracking module for at least one target app

**Definition of Done:**
- Scroll count increments correctly during manual testing in the target app
- Feature is clearly labeled as experimental in the UI and does not affect any other part of the app if it fails or the target app's UI changes

*This phase can be deprioritized or skipped without blocking any later phase, given its experimental nature.*

---

## Phase 8 — Windows: Tray App Shell

**Goal:** A working system tray presence with the popup window, reusing the backend already proven on Android.

**Tasks:**
- Scaffold the Electron or Tauri project (final choice made at this point, per `ARCHITECTURE.md` Section 13)
- Implement system tray icon and click-to-open popup window behavior
- Integrate the Supabase JS/TS client, implement login/logout
- Build the popup UI — list view, filtering, tick-confirm completion, typed input, completed-task history — mirroring the Android Main App's logic (Phase 4) exactly, per `DESIGN.md` Section 7.7
- Implement startup-on-login behavior

**Deliverables:**
- A fully functional Windows tray app, feature-equivalent to the Android Main App (minus voice)

**Definition of Done:**
- Logging in with the same account used on Android shows the same synced list immediately
- A task added on Windows appears on Android (and vice versa) within a few seconds
- All interaction patterns (filtering, tick-confirm, completed history) behave identically to Android's main app

---

## Phase 9 — Windows: Screen Time Agent & Reminders

**Goal:** Full feature parity with Android for the two remaining cross-cutting features.

**Tasks:**
- Build the active-window tracking background process
- Upload usage data to `screen_time_logs`, tagged with the Windows device identifier
- Confirm the existing unified dashboard logic (built in Phase 6) correctly displays combined Android + Windows data
- Integrate Windows toast notifications for the existing reminder system (Phase 5) — no new backend logic needed, only the delivery/display side

**Deliverables:**
- Screen time tracking active on Windows, visible in the unified dashboard
- Reminder notifications correctly delivered as Windows toasts

**Definition of Done:**
- Dashboard shows accurate combined screen time from both a Windows and an Android session for the same day
- A reminder correctly appears as a Windows toast notification at the expected time

---

## Phase 10 — Cross-Platform Integration Testing & Polish

**Goal:** Validate the complete system end-to-end and apply final visual/UX polish.

**Tasks:**
- Full bi-directional sync test: alternate creating/completing/editing items between Android and Windows repeatedly, confirm no data loss or duplication
- Verify persistent view-preference syncs correctly as the single source of truth across both platforms
- Verify all reminder scenarios once more in the fully integrated system
- Apply final design polish per the `DESIGN.md` deliverables checklist (Section 10) — finalized accent color, font, orb direction, exact glass component values, icon set, light mode
- Review and test accessibility considerations from `DESIGN.md` Section 9
- Write/update the top-level `README.md` with setup instructions

**Deliverables:**
- A stable, fully integrated MVP across both platforms

**Definition of Done:**
- All `PRD.md` Section 11 Success Criteria are met
- No known data-sync bugs between platforms
- Visual design matches the finalized direction from `DESIGN.md`

---

## Deferred / Post-MVP Phases (Not Part of This Build Plan)

To be scoped as their own future phases once the MVP above is complete and stable, per `PRD.md` Section 9:

- Quick utility commands (timer, alarm, calculator, weather)
- Google Maps integration
- Proactive nudges
- App categorization
- Data export
- iOS support
- Offline-first sync queue
- Wake-word voice activation

---

## Phase Summary Table

| Phase | Focus | Platform | Key Risk |
|---|---|---|---|
| 0 | Project setup | — | None — purely structural |
| 1 | Backend schema + auth + RLS | Supabase | Security misconfiguration |
| 2 | Core capture → sync loop | Android | Voice parsing accuracy, sync reliability |
| 3 | Orb UI & states | Android | Overlay permission + drag/snap feel |
| 4 | Main app (full management) | Android | Filter/completion logic correctness |
| 5 | Reminders | Supabase + Android | Scheduling accuracy, duplicate prevention |
| 6 | Screen time tracking | Android | Permission handling, background reliability |
| 7 | Reels counter (experimental) | Android | Fragility of accessibility-service hooks |
| 8 | Tray app shell | Windows | Feature parity with Android main app |
| 9 | Screen time + reminders | Windows | Cross-platform data consistency |
| 10 | Integration testing & polish | Both | Edge cases in bi-directional sync |

---

*End of Document*