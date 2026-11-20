# Control layer sprint — design

Status: **designs locked by the user on 2026-11-20** (dock, overlay UX, app classification). This document is the sprint-wide design authority. It supersedes `2026-11-20-time-blocking-editor-design.md` wherever that document assumes full-screen intents or touch counting; that spec remains the detailed visual reference for the block editor.

Visual references (approved in the browser companion): dock shell, both overlay variants, week → day → edit sheet. Keep the established Icarus identity: dark surfaces, amber used only for current state/anchors/primary action, mono numerals, hairline dividers, no new visual world.

## Scope and order

The user's six-step order is the sprint. Each step is its own implementation task; no step is called done until its tests/build pass.

1. Navigation dock — establish the four-destination shell and consistent screen structure.
2. Routine block editor — create/edit blocks, durations, priorities, per-block intervention settings.
3. App awareness — Usage Access, foreground detection, dwell time, classification, permission diagnostics.
4. Intervention state machine — policy evaluation, dedupe, cooldown, call suppression, dismissal.
5. Full-screen overlay — visual intervention, actions, fallback, accessibility-conscious dismissal.
6. Integration testing — complete flow on the Redmi, including reboots, denied permissions, background restrictions.

Deferred beyond this sprint: visual themes, notification redesign/custom sounds, launch splash. App restriction / screen-time enforcement is the *next* evolution, not this sprint.

## 1. Navigation dock

Four destinations, no fifth tab, no dashboard: **Today** (current block, countdown, next), **Routine** (week + block editor + rules), **History** (done, missed, reclaimed), **Settings** (permissions, app classification, interventions, overlay configuration).

Structure: `MainActivity` becomes the shell hosting four custom ViewGroups (no AndroidX/Fragments). The current status screen becomes `TodayView`; the other three are new. Dock is a compact rounded bar pinned above system insets, reused on every destination, with 48dp targets, one icon stroke weight, and amber only for the selected destination. System Back and predictive Back keep working; Back from a non-Today destination returns to Today.

Data: History derives from the stored 14 days of `DayState` (completed/skipped/plan). No new history store. Settings reads/writes only local preferences.

## 2. Routine block editor

As locked in the earlier spec/mockup: week list → day rail + rows → bottom edit sheet. Add per-block **intervention settings** to the sheet (intervention enabled, allowed dwell time, whether this block is "productive context"). Durations are edited through the same ±15-minute steppers. `RoutineStore` and `Routine.problems()` remain the only persistence/validation path; the raw JSON editor remains available as "Edit JSON directly" from Routine.

## 3. App awareness

**Primary signal: `UsageStatsManager`** with `PACKAGE_USAGE_STATS` (Usage Access). Read foreground package and dwell time; never read keystrokes, screen text, or notification contents. Because Usage Access can only be enabled by the user in Settings, the app detects the permission with `AppOpsManager` and shows a clear diagnostic row in Settings with a button that opens the system screen.

**Classification is manual.** Every package is `Unclassified` by default. The user assigns `Distraction` or `Productive` per package in Settings. Productive is a context signal, never a moral judgment; browser and YouTube start unclassified and are never guessed. Classification is stored as a local JSON map in SharedPreferences.

**Call suppression:** cellular calls via `TelephonyManager`/audio mode; third-party VoIP (WhatsApp, Snap, TikTok) is best-effort — foreground package plus voice-communication audio focus. This is stated honestly in Settings; no guarantee is promised. No AccessibilityService in this step.

## 4. Intervention state machine

One controller, fed by awareness signals, living in the foreground `TimekeeperService`. No touch counting. Policy examples:

| Condition | Action |
| --- | --- |
| App marked Distraction, ~2 min | Observe only |
| Same app, ~10 min during an active block | Consider intervention |
| Same app during a Productive context | Stronger intervention (shorter threshold) |
| Same app during unclassified/leisure context | No intervention |
| Any call active | Suppress; no overlay |
| After call ends | 90s grace |

Deduplication: one intervention per block per day; cooldown 2 min; snooze 5 min (late variant only); acknowledge/exit 10 min; hardware exit 2 min. A call kills a visible intervention immediately and gives the block a pass rather than a make-up nag. Signals while an overlay is up are dropped, not queued. Policy thresholds are constants in one file so they can be tested and tuned without hunting through the UI.

## 5. Full-screen overlay

`SYSTEM_ALERT_WINDOW` is the primary path (`TYPE_APPLICATION_OVERLAY` window, 7–10s, auto-dismiss). Android 14+ restricts full-screen intents to calling/alarm apps, so `USE_FULL_SCREEN_INTENT` is **not** relied on. If overlay permission is denied or revoked, fall back to a high-priority notification with the same actions and say why in Settings.

Two visual variants (approved): **late** — "Started one minute ago", Snooze 5 min + "I'm on it"; **still-scrolling** — "This block is running", no Snooze. Both auto-close, both show a draining line for remaining time, neither uses scolding copy. Accessibility-conscious dismissal is mandatory on both: Back, volume key, and long-press exit; a call removes the overlay instantly. Respect the system "remove animations" setting.

## 6. Integration testing and handoff

Automated: unit tests for classification/policy/dedupe/thresholds, routine edits, and history derivation; 0 lint errors; debug build succeeds. No emulator survives in this environment, so device verification is an explicit handoff: install on the Redmi and check reboots, permission denial/revocation, MIUI Autostart and battery settings, call suppression, overlay dismissal, dock insets, and font scale 1.3. Nothing is claimed as visually verified from code alone.

## Cross-cutting constraints

- Native Android Views, no AndroidX, minSdk 26 / targetSdk 36, edge-to-edge insets everywhere including the IME.
- Shared palette/type/ripple helpers so the four destinations and the overlay do not drift.
- `Actions` remains the only writer of day state; this sprint adds a separate policy/overlay state layer but never mutates routine or day state behind its back.
- No new permissions beyond Usage Access and Draw over other apps; no background screen capture, no accessibility service unless a later, separately-approved step proves one is genuinely required.
