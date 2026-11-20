# Icarus

A personal timekeeper, not a planner.

The plan is a weekly template. The phone's job is to keep you inside it: show
the current block in the shade, tell you ten minutes before a transition, ask
once if you're late, and — when you've lost an hour — offer a way to get the
important part of the day back.

No account. No backend. No AI. No root. One process, one file of schedule
JSON, and the Android alarm/notification APIs.

---

## What it actually does

**The shade (the product).** A low-importance, ongoing notification always
shows:

```
Hano development · 22m left
Next: Revision at 18:00
18:00 – 20:00
```

It counts down by itself (chronometer, no wakeups). When the block changes,
the notification changes. If you never open the app, this is still the app.

**Three alerts per block, maximum.**

| When | What |
| --- | --- |
| 10 min before | "10 min to Revision" — time to wrap up |
| at the start | "Revision starts now" |
| 10 min late (STRICT blocks only) | "Revision hasn't started" with *Started / Late 15 / Skip* |

No nagging, no streaks, no "you're falling behind!" copy. Ignore an alert and
the system assumes you made a decision.

**Recovery, on demand.** This is the part that matters.

> Anchors never move. Flexible time absorbs what you missed.

Suppose your evening was:

```
18:00–19:00  Revision          (FIRM)
19:00–20:00  Trading practice  (FLEX)
20:00–21:00  Free time         (FLEX)
21:00–22:00  Wind down         (ANCHOR)
```

It is now 20:30 and revision never happened. Tap **Recover day** and Icarus
proposes:

```
Preview
• Revision: partly reclaimed in 20:30–21:00 (30m of 60m)
```

Apply it and the plan for today becomes the freed 30 minutes of revision plus
the untouched wind-down. Nothing is deleted silently, nothing is stretched
past an anchor, and you approved it. The original 18:00 revision stays on the
day's record, struck through: the day stays honest.

If there is no flexible time left, it says so instead of inventing a slot:

```
• Revision missed — no flexible time left today
```

---

## Install

Requirements: Android 11+ (developed against SDK 36), JDK 17, Android SDK.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then, on the phone:

1. Open Icarus once. Accept the notification permission prompt.
2. Tap the status line if it says **exact alarms off** and allow
   *Alarms & reminders*.
3. **MIUI / Redmi only** — this is the difference between a timekeeper and a
   dead app:
   - Settings → Apps → Icarus → **Autostart**: on
   - Battery saver → **No restrictions**
   - Lock the app in recents (swipe down on the card → lock icon)

Without step 3 MIUI will kill the service when the screen is off, and the
persistent notification will vanish after a while.

---

## Editing the routine

Tap **Edit routine** in the app. It is plain JSON:

```json
{
  "version": 1,
  "days": {
    "MONDAY": [
      {
        "id": "mon-1800-revision",
        "title": "Revision",
        "start": "18:00",
        "end": "19:00",
        "category": "SCHOOL",
        "priority": "FIRM",
        "interrupt": "NUDGE"
      }
    ]
  }
}
```

- `category`: `SCHOOL`, `HANO`, `TRADING`, `PERSONAL`, `ADMIN`
- `priority`:
  - `ANCHOR` — immovable: school, sleep, appointments. Recovery never touches it.
  - `FIRM` — should happen today. If you miss it, recovery tries to reclaim it.
  - `FLEX` — filler. This is the time recovery is allowed to spend.
- `interrupt`:
  - `SILENT` — start alert only.
  - `NUDGE` — prepare + start.
  - `STRICT` — prepare + start + one overdue check.

Rules enforced on save: no overlapping blocks, no block crossing midnight,
unique ids. A bad edit is refused with the reason; the previous routine is
kept (and copied to `routine.broken.json` if it ever slips through).

Blocks must not cross midnight — a "sleep" block is the end of the day, not
the start of the next one.

---

## How it is built

```
engine/          pure Kotlin, no Android imports, fully unit tested
  Model.kt       Block, Priority, Interrupt, Routine
  Timeline.kt    template + today's state -> what should be happening now
  Recovery.kt    missed FIRM work -> reclaimed FLEX time
  NotifyPolicy.kt  the only three alerts, deduplicated and never stale

app/
  Actions.kt         the single mutator (screen and notification both use it)
  TimekeeperService  foreground service: live notification, alerts, re-arm
  Alarms.kt          next wake-up, exact when permitted
  Receivers.kt       notification buttons, alarms, boot/time changes
  data/              JSON routine + per-day state in SharedPreferences
  MainActivity.kt    status panel: now, next, recover, today
  RoutineEditorActivity.kt
```

Design notes worth keeping:

- **State is derived, never accumulated.** Every tick rebuilds today's
  timeline from `template + state`. A reboot, an app swipe, or a timezone
  change cannot desynchronise the clock.
- **The engine has no Android dependency**, so the interesting logic is
  testable on the JVM in milliseconds:

  ```bash
  ./gradlew testDebugUnitTest
  ```

- **Alerts are keys, not timestamps.** `blockId:kind` is marked sent when
  delivered, so a phone that was off for an hour does not dump a backlog.
  Alerts older than three minutes are dropped.
- **The service is a backstop, not the source of truth.** Exact alarms wake it
  after a kill; boot restarts it; the notification is rebuilt from disk.

---

## Tests

```
TimelineTest      now / gap / finished, skips, accepted plans, progress
RecoveryTest      reclaim, partial reclaim, anchors fixed, no overlaps
NotifyPolicyTest  three alerts, strict-only overdue, stale suppression
RoutinesTest      default routine validity, JSON round trip, evening recovery
DaySimulationTest a full week at one-minute resolution through the real path
```

Run `./gradlew testDebugUnitTest` (34 tests, a couple of seconds). The
invariants to protect if you change anything are in `DaySimulationTest`:
never two alerts for the same transition, never an alert outside its window,
never an overlapping or anchor-breaking recovery plan.

---

## Deliberately not built (yet)

- **Usage stats detection.** `PACKAGE_USAGE_STATS` could tell you "you've been
  on Instagram for 40 minutes" and trigger recovery automatically. Powerful,
  but it is surveillance of yourself; add it only if manual recovery stops
  being enough.
- **Weekly review.** A Sunday block that asks two questions and rewrites next
  week's template. Right now the template is edited by hand.
- **Inbox / capture.** Life admin currently lives in a block called "Life
  admin". If that block starts overflowing, a one-line capture list is the
  next small feature — not a task manager.
- **Anything that needs a server.**
