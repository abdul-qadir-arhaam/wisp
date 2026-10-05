# Design Document
## Wisp — UI/UX, Visual Theme & Interaction Design

**Version:** 1.0 (MVP Scope)
**Companion document to:** `PRD.md`, `ARCHITECTURE.md`

---

## 1. Design Philosophy

Wisp should feel like a **calm, ambient companion**, not a conventional app. The product's entire reason for existing is to reduce friction and cognitive load — so the design itself must reinforce that: minimal, uncluttered, quiet until needed, and premium in feel rather than busy or gimmicky.

Three principles should guide every screen and interaction:

1. **Glanceable over detailed.** Default views show the minimum needed to orient the user. Depth is one tap away, never forced upfront.
2. **Presence, not intrusion.** The orb (Android) and tray icon (Windows) exist to be *available*, not to demand attention. Motion, sound, and notifications should be used sparingly and purposefully.
3. **Consistency across platforms, adapted per context.** Android and Windows should clearly feel like the same product, using the same visual language — but each adapted to how that platform is actually used (touch + voice vs. mouse + keyboard).

---

## 2. Visual Theme: Glassmorphism

Glassmorphism is the signature visual identity across the entire product, chosen deliberately to create a premium, layered, "floating" feeling — which also reinforces the product's core metaphor (an ambient presence that sits *on top of* everything else).

### 2.1 Core Glassmorphism Properties
Every major surface (cards, panels, the orb's expanded state, modals) should use:

- **Background blur** — translucent background with a gaussian blur applied to whatever sits behind it (backdrop-filter / equivalent native blur effect)
- **Low-opacity fill** — a semi-transparent base color (roughly 10–20% opacity) over the blur, so content behind subtly shows through without being distracting
- **Soft border** — a thin, 1px light-colored border (often white at low opacity, ~15–25%) to define the edge of the glass surface, since blur alone can look edge-less
- **Soft, diffused shadow** — a low-intensity drop shadow beneath each glass surface to suggest it's physically floating above the layer behind it, not flat
- **Rounded corners** — consistently rounded (generous radius, e.g., 16–24px equivalent) across all cards and panels, never sharp edges, to keep the aesthetic soft and approachable

### 2.2 Layering & Depth
Glassmorphism works best with a clear sense of z-depth. Wisp should have **three conceptual layers**:

1. **Background layer** — the device's wallpaper/other apps, visible through blur where relevant (true for the orb only, since it floats over other content)
2. **Base surface layer** — the app's own background (a solid or softly gradiented dark/neutral base)
3. **Glass card layer** — individual UI elements (task cards, the orb's expanded panel, buttons) that sit on top of the base layer with the full glass treatment

This creates visual hierarchy without needing heavy borders or strong color contrast to separate sections.

---

## 3. Color Palette

### 3.1 Direction
A calm, premium, dark-first palette with a single accent color that glows subtly through glass surfaces. Avoid bright, saturated, "playful app" colors — the tone should feel more like a premium productivity tool (think calm focus apps) than a consumer social app.

### 3.2 Suggested Palette (Dark Mode — Primary)

| Role | Color | Usage |
|---|---|---|
| Base background | Deep charcoal / near-black (`#0D0F12`–`#12141A` range) | App background, behind all glass surfaces |
| Glass fill | White at ~12–18% opacity over base | Card/panel backgrounds |
| Glass border | White at ~18–25% opacity | Card/panel edges |
| Primary accent | Soft glowing teal or violet (e.g., `#7FE7D6` teal or `#A78BFA` violet — pick one as Wisp's signature color) | Mic button, active states, orb glow, primary CTAs |
| Secondary accent | A muted, desaturated variant of the primary accent | Secondary buttons, less prominent highlights |
| Success / completion | Soft green (desaturated, not neon) | Completion checkmarks, confirmed states |
| Warning / overdue | Soft amber-red (desaturated) | Overdue task flags |
| Text — primary | Off-white (`#F2F2F5` range) | Main content text |
| Text — secondary | Muted grey (`#9A9CA5` range) | Metadata, timestamps, secondary labels |
| Text — disabled/struck-through | Low-opacity grey | Completed task text |

### 3.3 Light Mode
Light mode should invert the base (light neutral background, e.g., soft off-white/light grey) while keeping the same accent color and glass logic (dark-tinted glass fill instead of white-tinted). Light mode is supported but **dark mode is the primary, default experience**, since glassmorphism and ambient glow effects read more premium against a dark base.

### 3.4 Accent Color Decision
Recommend settling on **one** signature accent color early (teal or violet are both strong candidates — teal feels calmer/more focused, violet feels more premium/techy) and using it consistently everywhere Wisp wants to draw attention: the orb's idle glow, the mic button, active filter selection, primary buttons. Avoid introducing additional accent colors beyond this one plus the functional success/warning colors.

---

## 4. Typography

- **Style:** Clean, modern, geometric or humanist sans-serif (e.g., something in the family of Inter, Manrope, or SF Pro — final font to be chosen during implementation, but the direction should be highly legible, minimal personality, not decorative).
- **Weight usage:** Minimal variation — primarily one regular weight for body text, one medium/semibold weight for headings and emphasis. Avoid using more than 2–3 weights across the whole app.
- **Hierarchy via size and opacity, not heavy weight changes** — e.g., secondary text uses a lighter color/opacity rather than a dramatically different size or boldness, to keep screens feeling calm rather than visually loud.
- **Legibility over glass surfaces:** since text sits on translucent, blurred backgrounds, ensure sufficient contrast — primary text should always test cleanly against both the lightest and darkest content likely to appear behind the glass.

---

## 5. Iconography & Motion

### 5.1 Icons
- Simple, line-based or softly-filled icon style — consistent stroke weight throughout.
- No heavily detailed or skeuomorphic icons — everything should feel light, matching the glass aesthetic.
- Functional icons only where necessary (mic, checkmark, filter, settings/account, screen-time) — avoid decorative icon clutter.

### 5.2 Motion Principles
Motion should be **subtle and purposeful**, reinforcing the "ambient, alive, calm" personality:

- **Orb idle state:** a soft, slow pulse or gentle glow animation — just enough to read as "alive," never distracting.
- **State transitions (idle → compact → expanded):** should *morph*, not abruptly switch — smooth scale/shape transitions, consistent with the Dynamic-Island-inspired interaction pattern.
- **Listening state:** a clear but calm visual indicator (e.g., a soft waveform or pulsing ring around the mic), giving the user confidence their voice is being captured.
- **Completion confirmation:** a quick, satisfying but understated animation (e.g., a checkmark draw-in, brief glow) — rewarding without being flashy.
- **Avoid:** bouncy/elastic easing, long animation durations (keep transitions fast, generally under ~300ms), or anything that delays the user from their next action.

---

## 6. Orb Design (Android) — Creative Direction

The orb is Wisp's core visual identity and the one element intentionally left open for creative exploration, within these constraints:

- **Must visually read as a single, cohesive object** across all three states (idle, compact, expanded) — it should feel like one shape morphing, not three unrelated UI elements swapping in and out.
- **Must not be a literal copy** of any existing product's design — inspired by the *interaction pattern* of a morphing pill/capsule, not its visual appearance.
- **Candidate directions to explore:**
  - A **soft glowing orb/sphere** — minimal, ambient, glows gently in the accent color at idle, brightens during listening
  - A **liquid blob** — organic, slightly fluid motion when idle, stretches/morphs fluidly into the expanded panel shape
  - A **capsule/pill shape** — more geometric and structured, closer to the Dynamic Island reference point, expands linearly into the panel
- **Idle state:** should be small enough to never meaningfully obstruct content underneath, positioned at a screen edge by default (per the snap-to-edge behavior).
- **Expanded state:** becomes a full glass panel (per Section 2), housing the snapshot list, mic button, and navigation buttons — the transition from orb to panel should feel like the same object growing, not a separate element appearing.

**Recommendation:** prototype 2–3 of the above directions visually (e.g., via the Google Stitch prompt already prepared) before committing, since this is the single most important piece of brand identity in the whole product.

---

## 7. Screen-by-Screen UX Specification

### 7.1 Android — Orb (Idle State)
- Small, unobtrusive, glowing accent-colored shape at a screen edge
- No text, no buttons visible
- Subtle idle animation only

### 7.2 Android — Orb (Compact/Listening State)
- Appears when voice capture is triggered
- Shows a clear but minimal "listening" indicator (waveform, pulsing ring, or similar)
- May also appear briefly after a query to show a glanceable result (e.g., a small badge: "3 due today") before shrinking back to idle

### 7.3 Android — Orb (Expanded State)
- Glass panel, morphed from the orb shape
- Contents, top to bottom:
  1. Snapshot list — upcoming tasks (no filters, no controls), recently completed tasks shown struck-through/greyed
  2. Mic button — prominent, accent-colored, clearly the primary action
  3. Two secondary buttons — "Screen Time" and "Open App"
- No account/login controls here (account state lives in the Main App only)

### 7.4 Android — Main App
- Full-screen, phone-optimized
- Top: account icon (opens account info + logout)
- Filter control — segmented control or dropdown: Priority / Oldest First / Newest First
- List — glass-card list items, each with:
  - Task/note content
  - Due date (if present), styled distinctly (e.g., small tag) — overdue items flagged clearly (warning color)
  - Tickbox with double-click-confirm interaction (empty → partial-fill on first tap → confirmed/struck-through on second tap)
- Recently completed items (today/yesterday) appear struck-through within the main list, auto-expiring after that window
- Separate tab/section: "Completed" — full history, grouped by date
- Floating mic button, consistently accessible
- Text input field as a fallback capture method

### 7.5 Android — Screen Time Analytics
- Prominent total screen time (today, unified across devices) at the top
- Simple bar or line chart — daily or weekly breakdown by app
- Top apps list — ranked, with time per app
- Separate, clearly labeled "experimental" section for short-form scroll count (reels)

### 7.6 Android — Login Screen
- Centered layout, Wisp's orb/logo motif prominent above the form
- Email/password fields
- "Sign in with Google" button
- No logout or account controls (pre-authentication only)

### 7.7 Windows — Tray Popup
- Compact popup window (not full-screen), same glass treatment scaled down
- Same list layout, filter control, and tick-confirm interaction as the Android Main App
- No mic button, no voice indicator anywhere in this UI
- Text input field for capture (primary input method on this platform)
- Completed section same hybrid model as Android
- Small account icon for logout

---

## 8. Interaction Patterns (Cross-Platform Consistency)

| Pattern | Android | Windows |
|---|---|---|
| Primary capture | Voice (push-to-talk) | Typed input |
| Fallback capture | Typed input | — (typed is already primary) |
| Task completion | Tick (double-click-confirm) or voice | Tick (double-click-confirm) only |
| View filtering | Voice command or UI control | UI control only |
| Confirmation feedback | Spoken (voice input) or visual (typed input) | Visual only, always |
| Presence | Floating orb, always visible | System tray icon, click to reveal |
| Account access | Main App only | Tray popup only |

This table should be treated as the canonical cross-platform behavior reference during implementation — any new feature added later should be mapped against it to decide its platform-specific behavior before being built.

---

## 9. Accessibility Considerations

- Maintain sufficient text contrast against glass surfaces in both light and dark mode — test against worst-case backgrounds, not just the default base color.
- Ensure the double-click-confirm tick interaction has an alternative for users who may have motor difficulty with precise double-taps (e.g., a sufficiently large tap target, forgiving timing window).
- Voice features should always have the typed-input fallback available, not just as an emergency case but as a genuine equal alternative for users who prefer or need it.
- Avoid relying on color alone to indicate state (e.g., overdue) — pair color with a label or icon as well.

---

## 10. Design Deliverables Checklist (for implementation handoff)

- [ ] Finalized accent color (teal vs. violet vs. alternative)
- [ ] Finalized font pairing
- [ ] Orb direction selected from creative exploration (Section 6)
- [ ] Full glass-surface component spec (exact blur radius, opacity values, shadow values) — translate the ranges in Section 2 into exact design tokens
- [ ] Icon set finalized (mic, tick/checkbox states, filter, account, screen-time, overdue flag)
- [ ] Light mode palette fully mapped from dark mode equivalents
- [ ] Motion/animation timing values documented (durations, easing curves) for each transition named in Section 5.2

---

*End of Document*