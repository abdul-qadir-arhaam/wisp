# Product Requirements Document (PRD)
## Wisp — A Voice-First Personal Assistant for Cross-Device Thought Capture

**Version:** 1.0 (MVP Scope)
**Status:** Planning Complete — Ready for Development
**Platforms:** Android (primary), Windows (companion)
**Author:** [Your Name]
**Last Updated:** [Date]

---

## 1. Executive Summary

Wisp is a personal assistant system designed to solve one specific, recurring problem: **the loss of fleeting thoughts, tasks, and ideas** that occur when a person is away from the device where they'd normally record them. Wisp allows a user to capture any thought instantly, by voice, on their phone — and have it appear, correctly structured and prioritized, on any other device they use, without manual re-entry or searching.

Wisp is represented on Android as a persistent floating orb — a lightweight, always-accessible presence inspired by the interaction pattern of iOS's Dynamic Island, but not a visual clone. On Windows, Wisp exists as a lightweight system tray companion, since the orb's floating-overlay metaphor is not appropriate for a desktop, mouse-and-keyboard context.

This document defines the full product scope, feature behavior, platform-specific requirements, data architecture, and explicit non-goals for the MVP.

---

## 2. Problem Statement

Users frequently have task-relevant thoughts, ideas, or obligations arise at moments when they are not at their primary work device (e.g., out and about, away from their laptop). Without immediate capture, these thoughts are:

- Forgotten entirely, or
- Captured in a disconnected, device-local tool (a native Notes app, a text message to self, etc.), requiring the user to later **remember that the thought exists** and manually search for it across devices.

This creates cognitive overhead and lost productivity, particularly for a user who regularly moves between a phone and a laptop/desktop while working on ongoing projects, applications, deadlines, or personal goals.

---

## 3. Product Vision & Goals

> **"Speak a thought once. It's there, correctly prioritized, wherever you open Wisp next."**

### Primary Goals
- Eliminate the friction of capturing a task or idea — one spoken sentence, no required fields, no app-switching.
- Guarantee that captured items sync reliably and near-instantly across all of a user's devices.
- Automatically structure captured input (deadline extraction, prioritization) without requiring the user to manually categorize anything.
- Give the user a unified, cross-device view of their screen time, as a secondary but complementary feature.
- Maintain a calm, ambient, non-intrusive presence — Wisp should feel like a companion, not another notification-heavy app.

### Non-Goals (for this MVP)
- Wisp is not a full productivity suite (no calendars, no collaboration/multi-user features, no project management structures beyond flat task/notes).
- Wisp is not replacing native OS utilities (timer, alarm, calculator, weather) in this version — see Section 9.
- Wisp is not a navigation app — Maps integration is deferred.
- Wisp is not targeting iOS in this version — see Section 10.

---

## 4. Target User

A single user (not multi-tenant/team), who:
- Regularly switches between a phone and a laptop/desktop throughout the day.
- Frequently has task- or idea-related thoughts occur away from their primary work device.
- Is comfortable with voice interaction as an input method.
- Currently relies on fragmented tools (native notes, reminders, memory) to track tasks and ideas.

This is initially a personal-use/portfolio project, designed for a single account holder, with architecture that does not assume multi-user support.

---

## 5. Core Architectural Principles

These principles govern every feature decision in this document and should be treated as binding constraints during development.

### 5.1 Action vs. Memory Separation
- **Memory** — anything the user expects to retrieve, reference, or see synced later (tasks, notes/ideas) is owned entirely by Wisp's own backend and data model. It is never delegated to a native OS app, because native apps do not reliably expose cross-platform sync.
- **Action** — fire-and-forget operations with no lasting content to retrieve (timer, alarm, calculator, weather, navigation) are intended to orchestrate existing native/OS systems rather than being rebuilt inside Wisp. *(Note: this category is deferred from MVP scope — see Section 9 — but the architecture should leave room for it.)*

### 5.2 Orchestration Over Reimplementation
Where utility-style features exist in Wisp (deferred for MVP, but architecturally anticipated), Wisp should control existing native systems (e.g., trigger the OS's own timer, trigger Google Maps navigation) rather than rebuilding that functionality internally. This keeps Wisp lightweight and avoids duplicating well-solved problems.

### 5.3 Click = View, Voice = Act (Android)
Tapping the orb opens a view (expanded snapshot, main app). Voice is the mechanism for performing actions (adding, completing, querying). This split keeps the interaction model predictable and avoids turning the orb into a button-heavy launcher, which would undermine the value of voice orchestration.

### 5.4 Platform-Appropriate Presence
Each platform gets a presence model suited to its context: a floating, draggable orb on mobile (where glanceable, always-on-top interaction makes sense), and a system tray icon on desktop (where the user is already keyboard/mouse-driven and a persistent floating overlay would be intrusive).

---

## 6. Feature Specification

### 6.1 To-Do / Notes System (Core Feature)

This is the centerpiece of the product. All other features are secondary to this loop working flawlessly.

#### 6.1.1 Data Model
Tasks and notes are **the same underlying data type** ("items"), differentiated only by optional fields. There is no separate notes system and no separate to-do system.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `user_id` | UUID | Owner, foreign key to auth user |
| `content` | Text | The captured thought/task, cleaned of filler words |
| `created_at` | Timestamp | Set at capture time |
| `due_at` | Timestamp (nullable) | Parsed from natural language at capture time, if present |
| `completed` | Boolean | Default `false` |
| `completed_at` | Timestamp (nullable) | Set when marked complete |
| `source_device` | Text | Which device created the item (for traceability/debug, not shown prominently to user) |
| `reminder_sent_day_before` | Boolean | Tracks whether the day-before push has been sent |
| `reminder_sent_overdue` | Boolean | Tracks whether the one-time overdue push has been sent |

#### 6.1.2 Capture
- **Input methods:** Voice (Android, primary), typed text (fallback on Android, primary on Windows).
- **No required fields.** A single spoken or typed sentence is sufficient to create an item.
- **No follow-up clarification questions** are asked during capture, under any circumstance — this is a hard product rule to preserve zero-friction capture.
- **Natural language deadline parsing:** phrases such as "today," "tomorrow," "by Friday," "next Monday," "5th Oct" must be parsed into a concrete `due_at` timestamp automatically, using an established date-parsing library (not custom-built NLU).
- **Filler stripping:** phrases like "I want to," "I need to," "remind me to" should be stripped from the stored `content` where feasible, leaving a clean task description.
- **Voice confirmation:** Wisp speaks a short confirmation (e.g., "Got it, saved") **only when the input method was voice**. Typed input receives a silent/visual confirmation only (e.g., a checkmark animation), never a spoken reply.

#### 6.1.3 Views & Filtering
Three selectable views apply to the **entire list** (both dated and undated items):

1. **Priority View (default)**
   - Overdue items first (visually flagged, e.g., red tag or "Overdue" label)
   - Then items sorted by soonest `due_at` ascending
   - Then undated items last, sorted oldest-created first
2. **Oldest First** — entire list sorted by `created_at` ascending, regardless of due date.
3. **Newest First** — entire list sorted by `created_at` descending, regardless of due date.

- The selected view is a **persistent user preference**, stored per account (not per device), and remains the default until explicitly changed again.
- View can be changed via voice command (Android) or UI control (both platforms).

#### 6.1.4 Completion
- **Tickbox with double-click confirmation** (both platforms):
  - First click: tickbox shows a visible partial-fill/highlight state, signaling "one more click to confirm."
  - Second click (within a short time window): registers completion.
  - If no second click follows within the timeout window, or focus is lost, the tickbox resets to its empty state.
- **Voice completion (Android only):**
  - User says a phrase like "mark [task] as done," "I finished [task]," or similar.
  - Wisp matches the spoken phrase against item content.
  - If exactly one strong match is found, it is marked complete automatically, with a spoken confirmation.
  - If two or more items match ambiguously, Wisp asks **one** clarifying question to disambiguate (the only case in the entire system where a clarification question is permitted).
- **Undo:** "mark as not done" / "undo that" is supported via voice (Android) or by unticking the box (both platforms), reversing the completion state.

#### 6.1.5 Completed Task Display (Hybrid Model — both platforms)
- **Main list:** completed items remain visible, shown struck-through and greyed out, **only if completed today or yesterday.** After that window passes, they are removed from the main list view automatically.
- **Completed section (separate tab/view):** permanent, full history of every completed item, organized by completion date/time. Items never disappear from this section.

#### 6.1.6 Reminders
- Applies automatically to **every item with a `due_at` set** — not opt-in per item.
- **Day-before reminder:** a push notification sent approximately 24 hours before `due_at`.
- **Same-day fallback:** if an item is created with less than 24 hours remaining before its `due_at`, a reminder is sent the same day (morning) instead of a day-before reminder.
- **Overdue follow-up:** exactly one additional push notification is sent at the moment an item becomes overdue (i.e., `due_at` has passed and `completed` is still `false`). No repeated/daily nagging reminders in v1.
- **Delivery:** push notification only. No voice/spoken delivery of reminders under any circumstance.
- **Platform delivery mechanism:** Android native notifications; Windows toast notifications.

---

### 6.2 Screen Time Tracking & Analytics

- **Tracked per-app, per-device**, on both Android and Windows.
  - Android: via `UsageStatsManager`.
  - Windows: via a local active-window tracking agent.
- **Unified dashboard:** screen time across all of the user's devices is aggregated into a single analytics view, showing:
  - Total screen time today (unified across devices)
  - Daily/weekly breakdown by app
  - Top apps list
- Accessible via a dedicated button from the Android orb's expanded view and from within the main app.

### 6.3 Reels / Short-Form Scroll Counter

- **Android-only.** Implemented via Android's Accessibility Service, hooking into supported apps' UI to detect short-form content scroll events.
- Explicitly labeled in the UI as **experimental / best-effort** — it is understood to be fragile and dependent on third-party app UI structure, which may change without notice.
- Displayed as a distinct, clearly separated section within the Screen Time Analytics dashboard, not blended into general app-usage metrics.
- No Windows equivalent (not applicable in a desktop context).

---

## 7. Platform-Specific UI/UX Requirements

### 7.1 Android

#### 7.1.1 The Orb
- A floating, persistent icon rendered on top of other apps.
- **Freely draggable**; on release, **snaps to the nearest screen edge.**
- Remembers its last position (per device).
- **States:**
  1. **Idle** — minimal, small, ambient presence (subtle glow/pulse animation acceptable).
  2. **Compact** — appears during active voice listening, or briefly to show a glanceable confirmation/badge (e.g., a task-count teaser) after a query.
  3. **Expanded** — morphs into a larger glass panel (see 7.1.2).
- **Activation:** tap to open expanded view (view action), or press-and-hold / tap mic icon for voice (act action). Push-to-talk only in v1 — no always-listening wake word (architecturally reserved for a future version).
- **Auto-hide:** orb suppresses or shrinks automatically during fullscreen apps, presentations, or similar focused-use contexts.

#### 7.1.2 Orb Expanded View (Quick Glance)
Lightweight snapshot only — no filtering controls, no full history.
- Snapshot of upcoming tasks (top N items)
- Recently completed tasks (struck-through/greyed, today/yesterday only)
- Mic icon — push-to-talk for quick add (voice capture) and voice-based completion
- Button: opens Screen Time Analytics
- Button: opens Main App

#### 7.1.3 Main App (Full Management View)
Phone-optimized, full-featured, functionally equivalent to the Windows popup (see 7.2) but laid out for a mobile screen.
- Full to-do/notes list
- Double-click-confirm tickboxes
- Full filtering controls (Priority / Oldest First / Newest First)
- Completed task history section (full archive, by date)
- Voice commands available (add, complete, query, change view)
- Typed input field as capture fallback
- Account icon (top corner) → account info + Logout

#### 7.1.4 Login Screen
- Shown only pre-authentication.
- Email/password fields, plus "Sign in with Google" option.
- No logout control (entry point only).

### 7.2 Windows

- **Presence:** system tray icon only. No floating orb, no persistent overlay, no voice input of any kind.
- **Interaction:** click tray icon → opens a small popup window.
- **Popup contents:**
  - To-do/notes list — same layout and logic as the Android main app, scaled to a compact desktop popup
  - Double-click-confirm tickboxes (visual fill-on-first-click cue)
  - Full filtering controls (Priority / Oldest First / Newest First)
  - Hybrid completed-task display (recent in main list, full history in separate section)
  - Typed input field to add tasks/notes (no mic — Windows has no voice capture)
  - Account icon → Logout
- **Reminders:** delivered as native Windows toast notifications.

### 7.3 Visual Design Direction
- **Glassmorphism** throughout — frosted/translucent surfaces, background blur, soft borders, subtle shadows, layered depth — for a premium, ambient feel.
- Simple, uncluttered layouts; generous spacing; minimal text density.
- Dark mode as the primary/preferred visual direction, with light mode supported.
- The orb's visual design is intentionally open to creative exploration (shape, motion, morph style) within the above constraints — not a literal copy of any existing product's visual design.

---

## 8. Data Architecture & Sync

### 8.1 Authentication
- Handled via **Supabase Auth.**
- Single user account per person; login required on both Android and Windows before Wisp becomes usable.
- Supports email/password and "Sign in with Google."

### 8.2 Database
- **Supabase (PostgreSQL)** — single centralized database, serving as the source of truth for all items (tasks/notes) and screen-time records.
- Every record is tagged with `user_id`; both platforms read/write against the same backend.

### 8.3 Real-Time Sync
- Implemented via **Supabase's realtime subscriptions** (Postgres change listeners).
- Each client (Android, Windows) subscribes to changes scoped to the authenticated user's own records.
- A change made on one device (new item, completion toggle, edit) should propagate to the other device's active session in near-real-time, without manual refresh.

### 8.4 Offline Behavior (v1 Limitation)
- **Not supported in v1.** Unlike some real-time database providers, Supabase does not include a built-in offline write queue.
- If a device has no connectivity at the moment of capture, the write attempt will fail, and the user will need to retry once connectivity is restored.
- This is an explicitly accepted, documented limitation for v1, not an oversight.
- **Deferred improvement:** a local-first architecture (local SQLite/Room cache with a background sync queue) is the identified solution path, to be considered post-MVP.

---

## 9. Explicitly Deferred Features (Post-MVP)

These were discussed and intentionally scoped out of v1. They are not rejected — they are deferred, and the architecture should not actively preclude adding them later.

| Feature | Description | Reason Deferred |
|---|---|---|
| Quick utility commands | Timer, alarm, calculator, weather — voice-orchestrated through native OS apps | Adds orchestration complexity before the core loop is proven |
| Google Maps integration | Voice-triggered start/stop of native Google Maps navigation | Secondary to core product; adds another native-integration surface |
| Proactive nudges | e.g., "You've used Instagram 40 minutes today, want a break?" | Requires a rules/thresholds engine not yet designed |
| App categorization | Auto-tagging apps as social/productivity/entertainment for richer analytics | Nice-to-have for analytics depth, not core to MVP |
| Data export | CSV/JSON export of tasks or screen-time data | Low priority, low cost — candidate for an easy early post-MVP addition |
| iOS support | Full Wisp experience on iPhone/iPad | Apple platform restrictions block persistent overlays entirely, and heavily restrict screen-time data access (see Section 10) |
| Offline-first sync queue | Local-first caching and background sync retry | Added complexity; only pursued if offline reliability becomes a real pain point in practice |
| Wake-word voice activation | "Hey Wisp" always-listening activation, vs. current push-to-talk | Adds always-on audio processing complexity and privacy considerations; push-to-talk is sufficient to prove the core loop |

---

## 10. Platform Constraints & Known Limitations

- **iOS is out of scope for v1.** Apple does not permit third-party apps to render persistent, draggable overlays on top of other apps — this is a hard platform restriction, not a technical gap that can be engineered around. Additionally, iOS screen-time data is locked behind Apple's `Family Controls`/`DeviceActivity` frameworks, which provide only limited, bucketed data and require special entitlements. A future iOS presence, if pursued, would necessarily use a different interface pattern (e.g., a widget), not the orb.
- **Reels counter is Android-only and inherently fragile**, since it relies on Android's Accessibility Service reading third-party app UI structure, which can change without notice when those apps update. This is a known, accepted trade-off, clearly surfaced to the user as "experimental."
- **Windows has no voice input** by product decision, not a technical limitation — this keeps the Windows build lightweight and matches natural desktop usage patterns (typing is already the dominant input method in that context).
- **No offline support in v1** (see Section 8.4).

---

## 11. Success Criteria (MVP)

The MVP will be considered successful if:

1. A task or note spoken into the Android app reliably appears on the Windows tray popup within a few seconds, correctly parsed (content + deadline, where applicable).
2. The reverse sync direction (Windows → Android) works with equivalent reliability.
3. Priority sorting correctly reflects the locked logic (overdue → soonest deadline → undated) without manual correction needed.
4. Deadline reminders fire at the correct time relative to `due_at`, without duplicate or missed notifications under normal connectivity conditions.
5. Task completion (tick, double-click confirm, and voice) behaves correctly and consistently across both platforms.
6. Screen time data from both Android and Windows is visible, correctly attributed per device, in a single unified dashboard.
7. The orb behaves correctly — drag, edge-snap, state transitions (idle/compact/expanded), and auto-hide during fullscreen use.

---

## 12. Build Sequence (Recommended)

1. **Backend foundation** — Supabase project setup: auth, database schema (Section 8), realtime subscriptions configured and tested with a minimal client.
2. **Android MVP — core loop only** — voice capture → parsing → Supabase write → read back on a second logged-in session, to validate the sync loop before building any UI polish.
3. **Android — orb UI** — idle/compact/expanded states, drag/snap behavior, voice integration into the orb.
4. **Android — main app** — full list, filtering, completion, history.
5. **Android — reminders** — push notification scheduling logic.
6. **Android — screen time tracking** — `UsageStatsManager` integration + dashboard.
7. **Android — reels counter** (optional, experimental, can slip to later).
8. **Windows — tray app shell** (Electron or Tauri — implementation choice still open) — tray icon, popup window, Supabase client wired to the same backend.
9. **Windows — screen time agent** — active-window tracking integration.
10. **Cross-platform testing** — validate full bi-directional sync, reminders, and completion behavior end-to-end.

---

## 13. Open Implementation Decisions (Non-Product, Deferred to Build Time)

- **Electron vs. Tauri** for the Windows shell — both support the required system tray + popup window pattern; final choice to be made based on performance/footprint preference during implementation.
- Exact visual design output (colors, orb motion style) — to be finalized from UI/UX exploration (e.g., Google Stitch generations) prior to frontend implementation.

---

*End of Document*