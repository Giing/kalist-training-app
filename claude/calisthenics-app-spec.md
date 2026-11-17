# Calisthenics App: Product & Technical Spec

> Spec for a coding session. It covers the context, the product requirements, the data model and the screens of an offline Android app for tracking bodyweight training.

---

## 1. Context

The user is getting back into calisthenics after a long break. They travel a lot, so they don't always have a pull-up bar, and they train for running in parallel. Their workouts must be **short (about 30 min), 2–3 times per week**, mostly full-body, with light leg work so they don't hurt the running.

The training program already exists (a PDF, an Excel tracker and an Obsidian note). The app replaces those with a single place to **plan, run and track** sessions.

### Training rules the app should support
- Alternate **template A** and **template B**: A-B-A one week, then B-A-B the next.
- **Format:** a circuit of 3 rounds (4 rounds from week 6), with 60–90 s of rest between rounds.
- **Warm-up (5 min):** arm circles, hip rotations, 10 squats, 10 knee push-ups.
- **Progression (8 weeks):**
  - Weeks 1–2: re-entry. Aim for the low end of each target range and stop 2–3 reps before failure.
  - Weeks 3–5: build. Once the top of the range is hit on every round, switch to the harder variant.
  - Weeks 6–8: intensify. Go to 4 rounds, or add an advanced variant.
  - Race week: a single session, upper body only.
- Don't schedule a session the day before a hard run or a long run. This can be a reminder text only; no running integration is required.

---

## 2. Platform & constraints

| Item | Decision |
|---|---|
| Platform | **Android only** |
| Language / UI | Kotlin + Jetpack Compose + Material 3 |
| Min SDK | 26 (Android 8.0); target the latest stable SDK |
| Architecture | Single-activity, MVVM (ViewModel + StateFlow), Repository layer, Hilt for DI |
| Persistence | **Everything is stored locally.** Room (SQLite) for data, DataStore (Preferences) for settings |
| Network | **None.** The app must not request the `INTERNET` permission |
| Accounts | None. Single user, no login |
| Language | UI strings in resource files. French first (the user is French-speaking), English as a second locale |

---

## 3. Functional requirements

### 3.1 Exercises
- The user can **create, edit, archive and delete** exercises.
- Fields:
  - Name (required)
  - Description / cues (optional)
  - **Target type: `REPS` or `TIME`** (required). This decides how the target, the logging input and the display work (reps vs seconds).
  - Per-side flag (e.g. lunges "/ leg", side plank "/ side")
  - Easier variant and harder variant (free text)
  - Equipment tag: `NONE`, `BAR`, `TABLE`, `CHAIR_BENCH`, `BAND` (this is the "no bar while travelling" case)
- An exercise used in past sessions can't be hard-deleted. It is **archived** instead: hidden from pickers, but kept in the history.

### 3.2 Training templates
- **A and B are templates.** They are seeded on first launch (see §6) and can be fully edited.
- The user can **create, edit, duplicate and delete** templates.
- Template fields:
  - Name, color (used in the calendar and on cards), an optional description
  - Number of rounds (default 3)
  - Rest between rounds, in seconds (default 90)
  - An **ordered list of exercises**, each with:
    - Target sets (default = number of rounds)
    - Target range: `min`–`max` reps, or `min`–`max` seconds for `TIME` exercises
    - An optional note (e.g. "pull-ups if there's a bar, otherwise table rows")
- Exercises can be reordered by drag-and-drop.

### 3.3 Calendar
- A monthly calendar view on the home screen.
- Each day shows a colored dot per session, using the template color. Completed sessions are filled dots; planned sessions are outlined dots.
- Tapping a day lists that day's sessions. From there the user can:
  - Open a completed session (read-only, with an edit option)
  - **Plan** a session (pick a template) for that day
  - Start a planned session
- Header stats: sessions this week vs the weekly goal (default 3, editable in settings) and the current streak in weeks.
- Optional: suggest which template comes next (A or B) based on the last completed session.

### 3.4 Running a training session
- Entry points: a "Start session" button on home (pick a template, with the next one in the A/B alternation preselected), or a planned session on the calendar.
- Session screen:
  - Shows the current exercise, round X / N, and a list of all exercises in the template.
  - **For each exercise, show three reference values:**
    1. **Previous:** the values logged per set the last time this exercise was done in a completed session (e.g. `8 · 7 · 6`), plus the variant used.
    2. **Goal:** the target range from the template (e.g. `3 × 6–12`, or `3 × 30–45 s`).
    3. **Best:** the best single set ever (max reps, or max seconds) and its date.
  - Input for the current set: a stepper (+/−) prefilled with the previous value for that set. For `TIME` exercises, a stopwatch / countdown with a manual edit option.
  - A variant field, prefilled with the variant used last time.
  - A **rest timer** between rounds that starts automatically, using the template's rest duration. It vibrates at the end and can be skipped.
  - A "progress" hint when the previous session hit the top of the range on every set ("Try the harder variant: …").
  - Sets and exercises can be skipped.
- Finishing a session:
  - Optional RPE / perceived effort from 1 to 10, plus notes.
  - Summary screen: total volume per exercise, new personal bests highlighted.
- Robustness: the session in progress is saved after every logged set, so a session interrupted by the app being killed resumes where it stopped. The screen stays on during a session.

### 3.5 History & progress (nice to have, in scope if time allows)
- Per-exercise detail: a list of past sessions plus a simple line chart (best set per session over time).

### 3.6 Admin / settings panel
- **Color theme:**
  - Mode: Light / Dark / System
  - Accent: a few presets (e.g. Orange `#E8572A`, Teal `#1F7A8C`, Indigo, Green, Pink) plus Material You dynamic color on Android 12+
  - Changes apply instantly and are saved in DataStore
- Weekly session goal (default 3)
- Default rest duration
- Units/locale (FR / EN)
- **Local data management** (still offline):
  - Export all data to a JSON file through Android's Storage Access Framework
  - Import from JSON (replace or merge)
  - Export session logs to CSV/XLSX, using the existing `Journal` columns (Date, Week, Session, Exercise, Variant, Set 1–4, Total, Best set, RPE, Notes)
  - Reset the app (with confirmation)

---

## 4. Data model (Room)

```text
Exercise
  id: Long (PK)
  name: String
  description: String?
  targetType: TargetType            // REPS | TIME
  perSide: Boolean
  easierVariant: String?
  harderVariant: String?
  equipment: Equipment              // NONE | BAR | TABLE | CHAIR_BENCH | BAND
  archived: Boolean = false
  createdAt: Instant

Template
  id: Long (PK)
  name: String
  description: String?
  colorArgb: Int
  rounds: Int = 3
  restSeconds: Int = 90
  createdAt: Instant

TemplateExercise
  id: Long (PK)
  templateId: Long (FK → Template, cascade)
  exerciseId: Long (FK → Exercise)
  position: Int
  targetSets: Int
  targetMin: Int                    // reps or seconds depending on Exercise.targetType
  targetMax: Int
  note: String?

Session
  id: Long (PK)
  templateId: Long? (FK → Template, set null on delete)
  templateNameSnapshot: String      // the history survives template edits/deletes
  date: LocalDate
  status: SessionStatus             // PLANNED | IN_PROGRESS | COMPLETED | SKIPPED
  startedAt: Instant?
  finishedAt: Instant?
  rpe: Int?                         // 1..10
  notes: String?

SessionExercise
  id: Long (PK)
  sessionId: Long (FK → Session, cascade)
  exerciseId: Long (FK → Exercise)
  position: Int
  variant: String?
  targetSetsSnapshot: Int
  targetMinSnapshot: Int
  targetMaxSnapshot: Int

SetLog
  id: Long (PK)
  sessionExerciseId: Long (FK → SessionExercise, cascade)
  setIndex: Int                     // 1-based
  value: Int                        // reps or seconds
  completedAt: Instant
```

**Snapshots:** a session copies the template's targets when it starts. Editing a template later must never change the history.

### Derived queries
- **Previous performance (exercise X):** the most recent `COMPLETED` session containing X → its `SetLog` values ordered by `setIndex`, plus its variant.
- **Best performance (exercise X):** `MAX(SetLog.value)` across completed sessions for X, with the date.
- **Goal:** `TemplateExercise` (or the snapshot during a session).
- **Next template:** the template that follows the last completed one in the A/B rotation. A simple rule is fine: the least recently used of the user's "rotation" templates.

---

## 5. Screens & navigation

Bottom navigation: **Calendar** (home) · **Templates** · **Exercises** · **Settings**

1. **Calendar / Home:** month grid, weekly stats, "Start session" button, day sheet.
2. **Templates list → Template editor:** fields, ordered exercise list with targets, an add-exercise picker.
3. **Exercises list → Exercise editor:** includes the REPS/TIME toggle and the archive option.
4. **Active session** (full screen, no bottom nav): current exercise card with Previous / Goal / Best, set input, round tracker, rest timer overlay.
5. **Session summary / detail:** used both right after finishing and when opened from the calendar.
6. **Settings / Admin:** theme, goals, rest default, data import/export, reset.

---

## 6. Seed data (first launch)

### Exercises
| Name | Type | Per side | Equipment | Easier | Harder |
|---|---|---|---|---|---|
| Push-ups | REPS | no | NONE | Knees / hands elevated | Feet elevated → archer |
| Rows (table / towel) | REPS | no | TABLE | More upright body | Horizontal, feet elevated |
| Chair dips | REPS | no | CHAIR_BENCH | Knees bent | Legs straight, feet elevated |
| Reverse lunges | REPS | yes | NONE | Shorter range | Jumping lunges |
| Plank | TIME | no | NONE | On knees | One limb lifted |
| Pull-ups | REPS | no | BAR | Table rows | Slow negatives (5 s) → weighted |
| Pike push-ups | REPS | no | NONE | Hands further from feet | Feet elevated |
| Diamond push-ups | REPS | no | NONE | Close grip on knees | Feet elevated |
| Bulgarian split squats | REPS | yes | CHAIR_BENCH | Static split squat | Assisted pistol squat |
| Hollow body hold | TIME | no | NONE | Knees bent | Arms overhead |
| Side plank | TIME | yes | NONE | Knee down | Top leg raised |

### Templates
**A: Push & core** (orange `#E8572A`, 3 rounds, 90 s rest)
1. Push-ups: 3 × 6–12
2. Rows (table / towel): 3 × 6–10
3. Chair dips: 3 × 8–12
4. Reverse lunges: 3 × 8–8 / leg
5. Plank: 3 × 30–45 s

**B: Pull & core** (teal `#1F7A8C`, 3 rounds, 90 s rest)
1. Pull-ups: 3 × max. Store this as range 1–10 with the note "max clean reps; table rows if no bar".
2. Pike push-ups: 3 × 6–10
3. Diamond push-ups: 3 × 6–10
4. Bulgarian split squats: 3 × 8–8 / leg
5. Hollow body hold: 3 × 20–30 s
6. Side plank: 3 × 20–30 s / side

---

## 7. Non-functional requirements
- Fully usable offline, with no network permission.
- Cold start under 1 s on a mid-range device. The active session must never lose a logged set.
- Accessibility: touch targets of at least 48 dp, TalkBack labels on the steppers and timers, readable in dark mode.
- Large numbers on the active session screen, usable with sweaty hands from about 1 m away.
- Room migrations are defined from v1. No destructive migration.

## 8. Testing
- Unit tests: the previous / best / next-template queries and the progression hint logic.
- Room DAO tests on an in-memory database.
- Compose UI tests: create an exercise (TIME), create a template, run a full session, check the summary and the calendar dot.
- Test that resuming after the process is killed in the middle of a session works.

## 9. Out of scope (v1)
Cloud sync, accounts, running / GPS integration, wearables, social features, notifications (possibly v2: a reminder on planned days).

## 10. Suggested build order
1. Project setup (Compose, Hilt, Room, DataStore, theme with dynamic accent)
2. Data layer + seed + DAO tests
3. Exercises CRUD → Templates CRUD
4. Active session flow (Previous / Goal / Best, rest timer, persistence)
5. Calendar + planning
6. Settings: theme, then JSON export/import, then CSV/XLSX export
7. History charts (if time allows)
