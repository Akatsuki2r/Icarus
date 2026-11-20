# Time-blocking editor — design

Status: **superseded in part by `2026-11-20-control-layer-sprint-design.md`** (overlay permission path and signal source changed). Retained as the detailed visual reference for the editor.

Status: **design locked by the user on 2026-11-20**. Mockup: `2026-11-20-time-blocking-editor-mockup.html` (approved visual reference).
This is slice 1 of 4. Slices 2–4 (notification redesign + custom sounds, state-based themes, launch splash) are explicitly out of scope here.

## Goal and success criteria

Replace hand-editing JSON with a phone-first editor for the weekly routine. Success looks like:

- A user can see the shape of their whole week in one screen.
- A user can open one day and see where the hours actually go, including gaps.
- Retiming a block takes a few taps, with no precision dragging and no syntax to remember.
- The routine JSON remains the single source of truth and stays human-readable/diffable.

## Product truth that must not change

- Personal, offline tool. No account, sync, cloud, or new permission.
- Native Android Views, no AndroidX, minSdk 26 / targetSdk 36.
- `RoutineStore` owns `routine.json`; `Routine.problems()` owns validation (overlaps, duplicate ids, midnight crossing).
- `Actions` is the only writer of day state; this slice edits the **template**, not `DayState`.
- The shade notification, foreground service, recovery algorithm, and main status screen keep working unchanged.
- `RoutineEditorActivity` (raw JSON) remains reachable as an advanced path; it is not deleted.

## Locked visual direction

Three screens, inheriting the existing Icarus dark world. Palette roles: `BG #0B0C0E`, `CARD #15181B`, `HAIRLINE #242A30`, `TEXT #F2F3F5`, `TEXT_DIM #C7CCD2`, `MUTED #7B838D`, `ACCENT #F2B84B`. Amber is reserved for **today**, anchors, selected control state, and the primary action. No new accent colors, no gradients, no card spam, no kicker labels above headings.

**1. Week.** Top app bar "Your week", subtitle with the Monday–Sunday date range. Seven rows, one per day: day name, `N blocks · Hh Mm` in muted text, and a thin proportional load bar to the right. Today's row gets a `CARD` surface and amber day label. Tap a day opens its editor. A quiet footer action "Edit JSON directly" opens the existing `RoutineEditorActivity`.

**2. Day.** Top app bar `‹ Friday`, subtitle with total blocks/hours. Immediately below, a **day rail**: a custom-drawn horizontal band spanning 00:00–24:00, with segments for each block and visible gaps; labels at 06 / 12 / 18. Anchor segments use amber, firm use muted grey, flex use a darker tone. Below it, one row per block showing start time (tabular numerals), title, and a small priority pill. The tapped block is highlighted with a `CARD` surface. A single amber FAB with `+` adds a block to this day.

**3. Edit sheet.** A Modal bottom sheet on a dimmed scrim: title "Edit block", a text field for the title, Start and End rows each with `−` / `+` 15-minute steppers and a hint "steps of 15 min", chip groups for Priority (`anchor / firm / flex`), Alerts (`silent / nudge / strict`), and Category, then `Delete` (outlined) and `Save` (filled amber). All touch targets are at least 48dp with 8dp between them. Text uses sp units and the Material type roles already approximated by the app's typefaces.

**Interaction rules**

- Sheet `Save` writes that one change through `RoutineStore.save()` immediately and closes the sheet. There is no staged/dirty state across screens; Back always just navigates.
- `Delete` asks for confirmation first ("Delete this block?" / Delete / Cancel). It is the only destructive action.
- `+` creates a block at the next free 1-hour slot after the last block, titled "New block", then opens the same sheet. Invalid/placeholder titles are rejected on Save with an inline hint.
- Steppers clamp to 00:00–23:45. If a change would make end ≤ start, the value does not change and an inline hint says "End must be after start" (or the start equivalent).
- System Back and the predictive Back gesture always work; each screen uses the existing `onBackInvokedDispatcher` pattern on API 33+ with the legacy override for older versions. While the edit sheet is open, Back dismisses the sheet first.
- Both new activities use the existing edge-to-edge inset pattern (system bars, and IME for the sheet's text field).

## Architecture

| Unit | Responsibility | Depends on |
| --- | --- | --- |
| `WeekEditorActivity` | Lists seven days with counts/load; entry point from the main screen; advanced JSON link | `RoutineStore`, `Routine`, theme tokens |
| `DayEditorActivity` | Day rail + block rows + FAB; hosts the edit sheet | `RoutineStore`, `Routine`, `DayRailView`, `BlockEditSheet` |
| `BlockEditSheet` | Modal bottom sheet (a `Dialog` with bottom gravity, not a new Activity); collects title/times/chips and validates | `RoutineStore`, `Routine` |
| `DayRailView` | Custom `View` that draws the 24-hour band from a day's blocks | `Routine`, palette tokens |
| `Routine` helpers | Immutable `withBlock(day, block)`, `removeBlock(day, id)`, `nextFreeHour(day)` in the engine | engine model only |
| `IcarusTheme` | Shared palette/type/ripple helpers extracted from the duplicated private `Colors` objects | Android framework only |

Data flow: `RoutineStore.load()` → in-memory `Routine` for display → user edits a `Block` → helper returns a new `Routine` → `RoutineStore.save(newRoutine)` → the current activity re-renders. The service picks up the change on its next tick / explicit `TimekeeperService.start()` call, exactly as the JSON editor does today.

Files touched: new Kotlin files above; `AndroidManifest.xml` (register two activities); `MainActivity.kt` (point "Edit routine" at `WeekEditorActivity`); existing `RoutineEditorActivity.kt` (keep, linked as advanced); `res/values/colors.xml` / `styles.xml` (shared tokens, bottom-sheet theme); engine `Model.kt`; tests.

## Error handling and edge cases

- Broken `routine.json`: `RoutineStore.load()` already falls back to defaults; editors must not overwrite the `.broken.json` copy.
- A day with zero blocks: day rail is a plain track; list shows an inviting empty state ("Nothing planned for Friday. Use + to add a block.").
- Overlapping times or duplicate ids from an edit: run `Routine.problems()` before saving; show the first problem inline in the sheet and keep it open.
- Overlong titles: single-line ellipsis in rows; full value in the sheet field.
- Blocks between 00:00 and 03:00 are legal today and must appear on the rail — hence the full 24-hour band.
- Font scale 1.3 and landscape: rows and sheet must not clip; use wrap_content heights and scrolling for the sheet.
- Re-saving the same values is a no-op; no duplicate ids are generated for "New block" (suffix if needed).

## Testing

- Unit tests: `Routine` helpers (add/replace/remove/next free hour), id uniqueness, validation integration, 15-minute stepping and clamping, day-rail fraction math extracted as a pure function.
- Existing 36 tests must stay green. `lintDebug` must stay at 0 errors. `assembleDebug` must succeed.
- No emulator/device is available in this environment: runtime checks (rail rendering, sheet gesture, 48dp targets, insets under MIUI) are a documented handoff to the user, not a claim of verification.

## Out of scope

Themes, notification redesign/custom sounds, splash/loading screen, drag-to-retime, multi-day selection, undo history, recurrence exceptions, importing/exporting files, and any state (`DayState`) mutation.
