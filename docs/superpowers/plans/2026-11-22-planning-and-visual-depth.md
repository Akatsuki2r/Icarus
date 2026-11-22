# Planning Surface and Visual Depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat palette with tonal themes, make block times editable with a redesigned time treatment, record every routine edit, and turn the Routine tab into a real Plan surface.

**Architecture:** `IcarusTheme` remains the single token provider; nine token rows gain `raised`, `accentSoft` and tinted text roles. A `TimeDisplayView` owns the large editable time UI used by the editor. `EditLog`/`EditLogJson` add a persisted, testable edit trail. `PlanView` replaces the Routine gateway while the existing deep editors stay reachable. No AndroidX, no new permissions.

**Tech Stack:** Kotlin 2.1.20, Android Views, JUnit 4, org.json, `./gradlew` wrapper.

**Spec:** `docs/superpowers/specs/2026-11-22-planning-and-visual-depth-design.md`

## Global Constraints

- Native Views only, no AndroidX; minSdk 26, targetSdk 36, Java 17.
- Package `dev.icarus.app`; engine in `dev.icarus.engine`, data in `dev.icarus.app.data`.
- `RoutineStore` + `Routine.problems()` remain the validation path; `Actions` remains the day-state writer.
- Edge-to-edge insets and 48dp touch targets; font scale 1.3 must not clip.
- Accent marks current state, anchors, selection and primary actions only.
- Every task ends with `./gradlew testDebugUnitTest assembleDebug lintDebug` green.

## Review Focus

- Unknown or missing theme tokens → no crash, fallback to Moss/Focus. Test: `IcarusThemeTest.tokenTableIsComplete`.
- Dwell/time edits crossing midnight or making end <= start → rejected inline, no wrap to 00:00. Test: `TimeEditTest.rejectsInvalidRange`.
- Corrupt edit log → empty trail, no data loss of routine. Test: `EditLogJsonTest.corruptFallsBack`.
- Back from Plan with the quick-add sheet open → sheet dismissed first. Test: manual device checklist.
- Missing per-block intervention fields on old JSON → defaults, not exception. Test: `RoutineJson` defaults test.

---

### Task 1: Tonal theme table

**Files:** `app/src/main/java/dev/icarus/app/IcarusTheme.kt`, `app/src/main/java/dev/icarus/app/ui/IcarusColors.kt`, `app/src/test/java/dev/icarus/app/IcarusThemeTest.kt`

**Interfaces:** `Tokens` gains `raised`, `accentSoft`; `IcarusTheme.tokens(app,state)` unchanged signature; `IcarusColors` gains `RAISED`, `ACCENT_SOFT`.

- [ ] **Step 1: Write failing tests** for all nine rows from the spec table: exact bg/accent, `raised` present, accent contrast ≥4.5, tinted `muted` not equal to plain grey `#7B838D`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests dev.icarus.app.IcarusThemeTest`; expect unresolved fields.
- [ ] **Step 3: Replace the token table** with the nine rows from the spec; add surface levels and a 1px same-hue top edge helper in `IcarusColors` (`fun cardEdge(): Drawable`).
- [ ] **Step 4: Run full verification; commit** `feat: tonal theme system with three surface levels`

### Task 2: Editable time display

**Files:** create `app/src/main/java/dev/icarus/app/ui/TimeDisplayView.kt`; modify `BlockEditSheet.kt`, `DayEditorActivity.kt`, `WeekEditorActivity.kt`; test `app/src/test/java/dev/icarus/app/TimeEditTest.kt`

**Interfaces:** `fun TimeDisplayView.setTimes(start: LocalTime, end: LocalTime)`; `var onTimesChanged: ((LocalTime, LocalTime) -> Unit)?`; `object TimeEdit { fun shift(time: LocalTime, minutes: Int): LocalTime?; fun validate(start: LocalTime, end: LocalTime): String? }`.

- [ ] **Step 1: Failing tests** — `shift(23:45, +15) == null` (no wrap), `shift(00:00, -15) == null`, `validate(10:00, 09:00) != null`, `validate(09:00, 10:00) == null`.
- [ ] **Step 2: Run them; expect failure.**
- [ ] **Step 3: Implement `TimeEdit` and `TimeDisplayView`** with large tabular numerals, arrow, duration chip, ±15 steppers and a direct `HH:mm` field. Replace the stepper rows in `BlockEditSheet` with it; show the same treatment in day/week rows.
- [ ] **Step 4: Full verification; commit** `feat: redesign and edit block times in place`

### Task 3: Edit trail

**Files:** create `app/src/main/java/dev/icarus/engine/EditLog.kt`, `app/src/main/java/dev/icarus/app/data/EditLogJson.kt`; modify `DayEditorActivity.kt`, `RoutineEditorActivity.kt`, `ui/HistoryView.kt`; tests `EditLogJsonTest.kt`

**Interfaces:** `enum class EditKind { CREATE, EDIT, MOVE, DELETE }`; `data class EditEntry(id, at: Instant, kind, day: DayOfWeek, before: Block?, after: Block?, note: String)`; `object EditLog { fun add(entries: List<EditEntry>, entry: EditEntry): List<EditEntry> }` newest-first, cap 200; `EditLogJson.toJson/fromJsonOrEmpty`.

- [ ] **Step 1: Failing tests** — ordering, cap at 200, round-trip, corrupt fallback.
- [ ] **Step 2: Run them; expect failure.**
- [ ] **Step 3: Implement engine/data pieces**, persist in prefs via `SettingsStore` helpers, record from every editor save/delete/create, and render a "Changes" section in History.
- [ ] **Step 4: Full verification; commit** `feat: record routine edits in an edit log`

### Task 4: Plan destination

**Files:** create `app/src/main/java/dev/icarus/app/ui/PlanView.kt`; modify `ui/DockView.kt`, `MainActivity.kt`, `ShellNavigationTest.kt`; delete `ui/RoutineHomeView.kt`

**Interfaces:** `Destination.PLAN` replaces `ROUTINE`; `PlanView.render()` shows a seven-day selector, selected day's blocks, "Plan something" quick-add and a link to `WeekEditorActivity`.

- [ ] **Step 1: Update the failing test** to expect `["TODAY","PLAN","HISTORY","SETTINGS"]`; run it and watch it fail.
- [ ] **Step 2: Implement** `Destination.PLAN`, `PlanView`, dock label, shell wiring and quick-add sheet using `BlockEditSheet`/`Times`.
- [ ] **Step 3: Full verification; commit** `feat: make Plan the weekly time-blocking surface`

## Self-Review

All four spec sections map to Tasks 1–4. The five Review Focus inputs have tests or a named device step in the owning task. Types (`Tokens`, `TimeEdit`, `EditEntry`, `Destination.PLAN`) are consistent across tasks. Device-only behaviour is not claimed as verified.
