# Icarus Control Layer Sprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the nine-step control layer — dock, block editor, app awareness, intervention state machine, overlay, integration checks, three themed palettes with state variants, notification sounds, and splash — on top of the existing timekeeper without changing its core behaviour.

**Architecture:** Keep the foreground `TimekeeperService` as the single runtime brain. `MainActivity` becomes a shell with four swappable custom ViewGroups and a persistent dock; awareness writes signals into an `InterventionController`; the controller decides and asks an `OverlayGateway` to show either a `SYSTEM_ALERT_WINDOW` overlay or a notification fallback. All colour decisions come from one `IcarusTheme` token provider. No AndroidX, no Fragments, no new runtime dependency.

**Tech Stack:** Kotlin 2.1.20, Android Views, AGP 8.12, compileSdk/targetSdk 36, minSdk 26, JUnit 4 + org.json for JVM tests, `./gradlew` wrapper.

**Spec:** `docs/superpowers/specs/2026-11-20-control-layer-sprint-design.md` and, for the editor's visuals, `docs/superpowers/specs/2026-11-20-time-blocking-editor-design.md`.

## Global Constraints

- Native Android Views only; **no AndroidX**, no Fragments, no new runtime dependencies. minSdk 26, targetSdk 36, Java 17.
- Package `dev.icarus.app`; engine classes in `dev.icarus.engine`; data stores in `dev.icarus.app.data`.
- `RoutineStore` + `Routine.problems()` remain the only routine persistence and validation path. `Actions` remains the only writer of `DayState`.
- Edge-to-edge insets (status bar, nav bar, IME) on every screen; 48dp minimum touch targets; 8dp between them.
- Product copy: no "behind", "failed", "streak", or nagging. An ignored alert is a decision.
- Accent marks only current state, anchors and primary actions. No decorative motion, gradients-as-decoration, or new visual identity.
- Verification for every task: `./gradlew testDebugUnitTest assembleDebug lintDebug` with 0 failures, 0 lint errors.
- No emulator survives in this environment; device feel-checks are explicit handoffs, never claimed as verified.

## Review Focus

1. **Usage Access denied or revoked** — must show a diagnostic and degrade, never crash or silently pretend awareness works. Test: `SettingsStoreTest.hasUsageAccessFalseIsReported` in Task 3.
2. **Call starts while overlay is visible** — overlay must disappear immediately and grant 90s grace; no make-up nag. Test: `InterventionControllerTest.callKillsOverlayAndStartsGrace` in Task 4.
3. **Draw-over-apps denied** — must fall back to a notification with the same actions, not lose the intervention. Test: `OverlayGatewayTest.deniedUsesFallback` in Task 5.
4. **Overlapping or duplicate block edit** — must be rejected with the reason visible, never saved. Test: `RoutineEditTest.overlapRejected` in Task 2.
5. **System font scale 1.3 / insets / call UI overlap** — no clipping or trapped user. Test: manual Redmi checklist in Task 6, plus `IcarusThemeTest.contrastFloor` in Task 7.

---

## Phase 1 — Navigation dock

### Task 1: Shell with four destinations

**Files:**
- Modify: `app/src/main/java/dev/icarus/app/MainActivity.kt`
- Create: `app/src/main/java/dev/icarus/app/ui/TodayView.kt`, `ui/DockView.kt`, `ui/RoutineHomeView.kt`, `ui/HistoryView.kt`, `ui/SettingsView.kt`
- Test: `app/src/test/java/dev/icarus/app/ShellNavigationTest.kt`

**Interfaces:**
- Consumes: existing `Actions`, `RoutineStore`, `DayStateStore`.
- Produces: `enum class Destination { TODAY, ROUTINE, HISTORY, SETTINGS }`; `TodayView(context)`, `RoutineHomeView(context)`, `HistoryView(context)`, `SettingsView(context)` each exposing `fun render()`; `DockView(context, onSelect: (Destination) -> Unit) { fun select(d: Destination) }`.

- [ ] **Step 1: Write the failing test for destination mapping**

```kotlin
class ShellNavigationTest {
    @Test fun `four destinations are fixed and ordered`() {
        assertEquals(listOf("TODAY", "ROUTINE", "HISTORY", "SETTINGS"),
            Destination.entries.map { it.name })
    }
}
```

- [ ] **Step 2: Run it and confirm it fails** — `./gradlew testDebugUnitTest --tests dev.icarus.app.ShellNavigationTest`; expect unresolved `Destination`.

- [ ] **Step 3: Create `Destination` and the four view stubs.** Move the existing `buildUi()` body of `MainActivity` verbatim into `TodayView` (same insets logic, same card, same buttons). `MainActivity` keeps only a root `FrameLayout` plus `DockView`, swapping child visibility:

```kotlin
private fun show(d: Destination) {
    content.removeAllViews()
    content.addView(when (d) {
        Destination.TODAY -> todayView
        Destination.ROUTINE -> routineView
        Destination.HISTORY -> historyView
        Destination.SETTINGS -> settingsView
    })
    dock.select(d)
}
```

Other three views start with a title and an honest empty note; they are filled in later tasks.

- [ ] **Step 4: Run tests, build and lint**

Run: `./gradlew testDebugUnitTest assembleDebug lintDebug`
Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **Step 5: Commit** — `git commit -am "feat: navigation shell with four destinations"`

## Phase 2 — Routine block editor

### Task 2: Week, day and edit sheet with validation

**Files:**
- Create: `app/src/main/java/dev/icarus/app/WeekEditorActivity.kt`, `DayEditorActivity.kt`, `BlockEditSheet.kt`, `DayRailView.kt`
- Modify: `app/src/main/java/dev/icarus/engine/Model.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/dev/icarus/app/ui/RoutineHomeView.kt`
- Test: `app/src/test/java/dev/icarus/app/RoutineEditTest.kt`, `app/src/test/java/dev/icarus/engine/DayRailTest.kt`

**Interfaces:**
- Consumes: `RoutineStore.load()/save()`, `Routine.problems()`, palette tokens from Task 7 (until then use current colours).
- Produces: `fun Routine.withBlock(day: DayOfWeek, block: Block): Routine`; `fun Routine.removeBlock(day: DayOfWeek, id: String): Routine`; `fun Routine.nextFreeHour(day: DayOfWeek): LocalTime`; `fun dayRailFraction(time: LocalTime): Float` in `DayRailView.Companion`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun `overlapRejected`() {
    val v = Routine(mapOf(DayOfWeek.MONDAY to listOf(
        Block("a", "A", LocalTime.of(8,0), LocalTime.of(9,0), Category.SCHOOL),
        Block("b", "B", LocalTime.of(8,30), LocalTime.of(9,30), Category.SCHOOL))))
    assertTrue(v.problems().any { it.contains("overlaps") })
}
@Test fun `fraction maps midnight to zero and noon to half`() {
    assertEquals(0f, dayRailFraction(LocalTime.MIDNIGHT), 0.001f)
    assertEquals(0.5f, dayRailFraction(LocalTime.NOON), 0.001f)
}
```

- [ ] **Step 2: Run them; confirm they fail.**

- [ ] **Step 3: Implement engine helpers and `DayRailView`** using the approved mockup: week rows with count/hours/load bar, day rail 00:00–24:00, rows with time + title + priority pill, FAB `+`, bottom sheet with ±15 min steppers and chips. `BlockEditSheet` calls `withBlock` then `problems()`; on error it shows the first problem inline and stays open.

- [ ] **Step 4: Register both activities in the manifest** and point Routine home at `WeekEditorActivity`. Keep `RoutineEditorActivity` reachable as "Edit JSON directly".

- [ ] **Step 5: Run tests, build, lint; commit** — `"feat: weekly routine block editor"`

## Phase 3 — App awareness

### Task 3: Usage Access, dwell time and classification

**Files:**
- Create: `app/src/main/java/dev/icarus/app/data/SettingsStore.kt`, `app/src/main/java/dev/icarus/app/AppUsageReader.kt`
- Modify: `ui/SettingsView.kt`, `AndroidManifest.xml`
- Test: `app/src/test/java/dev/icarus/app/SettingsStoreTest.kt`, `AwarenessPolicyTest.kt`

**Interfaces:**
- Consumes: `UsageStatsManager`, `AppOpsManager`, `SharedPreferences`.
- Produces: `enum class Classification { UNCLASSIFIED, DISTRACTION, PRODUCTIVE }`; `class AppUsageReader(context) { fun hasAccess(): Boolean; fun foreground(): String?; fun dwellMillis(pkg: String): Long }`; `class SettingsStore(context)` with `classifications(): Map<String,Classification>`, `setClassification(pkg, kind)`, `appTheme()`, `themeMode()`, `chimeFor(kind)`, `setChime(kind, value)`.

- [ ] **Step 1: Write failing tests** — `hasUsageAccessFalseIsReported` (reader returns false, Settings shows the diagnostic string), `unclassifiedByDefault` (unknown package is `UNCLASSIFIED`), and `productiveContextChangesPolicy` (distraction app + productive foreground => shorter threshold).

- [ ] **Step 2: Run them; confirm failure.**

- [ ] **Step 3: Implement** with `android.permission.PACKAGE_USAGE_STATS` in the manifest, an `AppOpsManager` check for `android:get_usage_stats`, and a Settings row that opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Classifications live in a JSON map in prefs; no package is classified by default. Record foreground and dwell time in memory only.

- [ ] **Step 4: Tests, build, lint; commit** — `"feat: usage access and manual app classification"`

## Phase 4 — Intervention state machine

### Task 4: Centralized controller

**Files:**
- Create: `app/src/main/java/dev/icarus/app/InterventionController.kt`, `data/InterventionPolicy.kt`
- Modify: `TimekeeperService.kt`
- Test: `app/src/test/java/dev/icarus/app/InterventionControllerTest.kt`

**Interfaces:**
- Consumes: `Moment`, `DayState`, `AppUsageReader`, `SettingsStore`.
- Produces: `enum class InterventionKind { LATE, STILL_SCROLLING }`; `data class InterventionRequest(val kind, val blockId)`; `class InterventionController { fun onTick(now: LocalTime, moment: Moment, usage: AppUsageReader); fun onCall(active: Boolean); fun snooze(); fun acknowledge(); fun hardwareExit(); var pending: InterventionRequest?; fun clear() }`.

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun callKillsOverlayAndStartsGrace() {
    val c = InterventionController()
    c.onTick(LocalTime.of(19,10), activeMoment, usage)
    assertNotNull(c.pending)
    c.onCall(true)
    assertNull(c.pending)
    c.onCall(false)
    assertFalse(c.shouldIntervene(LocalTime.of(19,11))) // 90s grace
}
@Test fun lateFiresOncePerBlock() { /* two ticks, one request */ }
```

- [ ] **Step 2: Run them; confirm failure.**

- [ ] **Step 3: Implement the policy** in `InterventionPolicy.kt` as constants: late = start + 1 min; distraction observe = 2 min; evaluate = 10 min; productive context shortens threshold; call = suppress + 90s grace; snooze 5 min; acknowledge 10 min; hardware exit 2 min; cooldown 2 min; one late per block per day. No touch events. Drop signals while `pending != null`.

- [ ] **Step 4: Wire one `onTick` call into the existing 20s service tick; tests, build, lint; commit** — `"feat: centralized intervention policy"`

## Phase 5 — Overlay

### Task 5: Overlay gateway with notification fallback

**Files:**
- Create: `app/src/main/java/dev/icarus/app/OverlayGateway.kt`, `ui/InterventionOverlayView.kt`
- Modify: `TimekeeperService.kt`, `Notifier.kt`, `AndroidManifest.xml`, `Contract.kt`
- Test: `app/src/test/java/dev/icarus/app/OverlayGatewayTest.kt`

**Interfaces:**
- Consumes: `InterventionRequest`, `IcarusTheme.tokens()`, `Notifier`.
- Produces: `class OverlayGateway(context) { fun canShow(): Boolean; fun show(req: InterventionRequest); fun dismiss(); fun showFallback(req: InterventionRequest) }`.

- [ ] **Step 1: Write failing tests** — `deniedUsesFallback` (canShow false => notification path called, no window), `callDismissRemovesWindow`, `snoozeAndExitBothDismiss`.

- [ ] **Step 2: Run them; confirm failure.**

- [ ] **Step 3: Implement** `TYPE_APPLICATION_OVERLAY` full-screen window with `SYSTEM_ALERT_WINDOW`, 7–10s auto-dismiss, both approved variants, draining line, Snooze only on LATE, Back/volume/long-press exit, and immediate `dismiss()` on `onCall(true)`. If permission is missing, call `showFallback()` which posts a high-priority notification with the same actions. Do not use `USE_FULL_SCREEN_INTENT`.

- [ ] **Step 4: Tests, build, lint; commit** — `"feat: intervention overlay with fallback"`

## Phase 6 — Integration checks

### Task 6: Full-flow verification

**Files:**
- Modify: `app/src/test/java/dev/icarus/app/DaySimulationTest.kt`, `README.md`
- Test: manual Redmi checklist in README.

- [ ] **Step 1: Add a JVM test** that drives routine edit → awareness classification → policy tick → overlay request → dismissal in one flow, with the service mocked out of the controller.

- [ ] **Step 2: Add the device checklist to README** — reboot, permissions denied/revoked, MIUI Autostart + battery, call suppression, dock insets, font scale 1.3, overlay exit. Mark each as unverified until the user runs it.

- [ ] **Step 3: Full build; commit** — `"test: control layer integration flow"`

## Phase 7 — Three app themes with state variants

### Task 7: Token provider and theme settings

**Files:**
- Create: `app/src/main/java/dev/icarus/app/IcarusTheme.kt`
- Modify: every view created above, `Notifier.kt`, `SettingsView.kt`
- Test: `app/src/test/java/dev/icarus/app/IcarusThemeTest.kt`

**Interfaces:**
- Consumes: `Moment`, `SettingsStore`, overlay state.
- Produces: `enum class AppTheme { ICARUS, TIDE, EMBER }`; `enum class ThemeState { FOCUS, RECOVERY, WIND_DOWN }`; `data class Tokens(bg, surface, outline, text, textDim, muted, accent, good, warn : Int)`; `object IcarusTheme { fun tokens(app: AppTheme, state: ThemeState): Tokens; fun resolve(context: Context): Tokens }`.

- [ ] **Step 1: Write failing tests** — all 9 token rows have the exact hex from the spec, `resolve` picks `TIDE/RECOVERY` from stored settings, and `contrastFloor` asserts accent-on-bg ≥ 4.5:1.

- [ ] **Step 2: Run them; confirm failure.**

- [ ] **Step 3: Implement** the token table, `resolve()` reading `SettingsStore`, a 250ms crossfade when the resolved state changes, and replace all hard-coded colour objects in views and `Notifier`. Settings gets the app-theme picker and mode override.

- [ ] **Step 4: Tests, build, lint; commit** — `"feat: three app themes with state variants"`

## Phase 8 — Notification redesign and sounds

### Task 8: Three channels, local chimes, sound picker

**Files:**
- Create: `tools/gen_chimes.py`, `app/src/main/res/raw/chime_prepare.wav`, `chime_start.wav`, `chime_overdue.wav`
- Modify: `Notifier.kt`, `Contract.kt`, `SettingsView.kt`, `TimekeeperService.kt`
- Test: `app/src/test/java/dev/icarus/app/NotifierContentTest.kt`

- [ ] **Step 1: Write a failing test** that the live notification title/text no longer contains the duplicate progress string and that three distinct channel ids are declared in `Contract`.

- [ ] **Step 2: Run it; confirm failure.**

- [ ] **Step 3: Generate chimes** with a committed Python script using the `wave` module (under 1s each, no downloads), add `prepare/start/overdue` channels in `Notifier.ensureChannels()`, delete the legacy channel after migration, let `SettingsView` pick a chime or system default and update the channel sound in place.

- [ ] **Step 4: Tests, build, lint; commit** — `"feat: themed notifications with custom sounds"`

## Phase 9 — Splash

### Task 9: Platform cold-start treatment

**Files:**
- Create: `app/src/main/res/values-v31/styles.xml`, `app/src/main/res/drawable/splash_background.xml`
- Modify: `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/styles.xml`
- Test: none executable on JVM; verify with `./gradlew assembleDebug lintDebug` and a device handoff note.

- [ ] **Step 1: Add `values-v31/styles.xml`** with `windowSplashScreenBackground` and `windowSplashScreenAnimatedIcon` pointing at existing launcher art; no new Activity.

- [ ] **Step 2: Add the pre-31 static window background drawable** (centered launcher icon, bg colour) to the launcher activity theme.

- [ ] **Step 3: Build and lint; commit** — `"feat: platform splash with no added latency"`

## Self-Review

- Spec coverage: all nine spec sections map to Tasks 1–9; the five Review Focus inputs have tests in Tasks 3, 4, 5, 2 and 6/7 respectively.
- Placeholders: none — every code step carries concrete names, values and commands.
- Type consistency: `Destination`, `Classification`, `InterventionRequest`, `IcarusTheme.Tokens`, `SettingsStore` signatures are identical everywhere they are used.
- Device-only checks are named, not claimed, in Task 6 and the README.
