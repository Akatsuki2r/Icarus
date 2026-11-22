# Planning surface, editable times and tonal themes — design

Status: **direction approved by the user on 2026-11-22**. This document supersedes the flat-palette parts of `2026-11-20-control-layer-sprint-design.md` (§7). Product behaviour, permissions and engine rules from that spec remain in force.

## Goal

Make Icarus feel like something you can actually plan in, and make the visual identity feel natural and layered rather than a fixed dark palette. Specifically: a real Plan destination, editable times with a redesigned time type treatment, an edit trail in the engine, and three tone-rich themes with background depth.

## 1. Themes — natural tones with depth

Three app themes, each with three state variants (Focus / Recovery / Wind down). Every theme owns one hue family so the mixture stays uniform even though it uses several tones.

| Theme | Focus bg / accent | Recovery bg / accent | Wind down bg / accent | Text family |
| --- | --- | --- | --- | --- |
| **Moss** | `#10140F` / `#B7C98A` | `#0F1412` / `#8FC2AE` | `#0C100E` / `#A8B89A` | warm off-white `#EDF0E6` |
| **Clay** | `#161210` / `#D89B72` | `#141311` / `#C9AE82` | `#100D0C` / `#C48F85` | sand `#F5EDE4` |
| **Dusk** | `#0D1116` / `#8FB6D9` | `#0C1117` / `#9BB8CE` | `#090C11` / `#A7A6C9` | cool white `#E8EEF5` |

Each row also defines `surface`, `raised`, `outline`, `accentSoft` and tinted `textDim`/`muted` values in the token table. Depth comes from three stepped surfaces and a 1px same-hue top edge on cards; no gradient decoration. Accent is reserved for current state, anchors, selection and primary actions. Auto state derives from `Moment` + recovery plan + overlay, with the existing manual override.

## 2. Typography and editable times

`TimeDisplay`: start and end in large tabular numerals, a small arrow between them, and a duration chip. `HH:mm` values are editable through ±15-minute steppers and a direct text field; changes validate `end > start` inline. Week rows show times directly; day rows show them prominently and open the same editor. Font scale 1.3 must not clip.

## 3. Engine edit trail

`EditEntry(id, at, kind, day, before, after, note)` with `CREATE / EDIT / MOVE / DELETE`. `EditLog` keeps entries newest-first, capped at 200, serialised as JSON in prefs via a pure `EditLogJson` boundary. `DayEditorActivity` and the JSON editor record actual edits; History shows a "Changes" section. No silent mutation of the routine.

## 4. Plan destination

Rename the **Routine** dock item to **Plan**. `PlanView` becomes the planning surface: a seven-day selector, the selected day's blocks with editable times, and a "Plan something" quick-add sheet (title, day, start, end, category, priority, interrupt). `WeekEditorActivity` / `DayEditorActivity` remain as deeper editors, linked from Plan rather than standing in for it. No fifth destination.

## Architecture

- `IcarusTheme` token table expanded with `raised`, `accentSoft`, tinted text roles and the nine new rows. `IcarusColors` stays the getter-based view into active tokens.
- `TimeDisplayView` custom view; `BlockEditSheet` uses it for start/end.
- `EditLog` + `EditLogJson` in engine/data; `SettingsStore` or a dedicated store persists entries.
- `PlanView` replaces `RoutineHomeView`; `Destination.ROUTINE` becomes `Destination.PLAN` and `ShellNavigationTest` is updated.
- Shared back/inset behaviour unchanged.

## Testing

Unit tests for all nine token rows and contrast, time editing/clamping, edit-log ordering/cap/round-trip/corrupt fallback, and the four-destination order. Full `testDebugUnitTest assembleDebug lintDebug` must pass. Device checks (fonts, depth, keyboard, MIUI) are a documented handoff.

## Out of scope

New permissions, app restriction / screen-time enforcement, cloud sync, recurring exceptions, and a full calendar month grid.
