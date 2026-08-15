# Phone Away

An Android app that pays you for not touching your phone. Start an idling
session, put the phone down, and earn points for every minute you leave it
alone. Harder-to-cheat modes pay more.

## The four modes

You choose how strictly the app checks up on you. The multiplier rises with how
hard the mode is to fake, so the honest options are also the lucrative ones.

| Mode | Multiplier | What ends the session |
| --- | --- | --- |
| **Honor** | ×1.0 | Only you, by tapping "I gave up". Nothing is enforced. |
| **App Open** | ×1.25 | Switching apps or going to the home screen. |
| **Screen Locked** | ×1.75 | Unlocking the phone. Glancing at a locked screen is fine. |
| **Hands Off** | ×2.5 | Unlocking the phone, or picking it up at all. |

Screen Locked and Hands Off do not start counting until the screen actually goes
off — the session sits in an *arming* state first, so you cannot earn the
top rate while staring at the phone.

## How points work

```
points = minutes × 10 × mode × duration bonus × streak bonus
```

- **Linear in time**, so ten minutes is always worth ten minutes.
- **Duration bonus** rewards long unbroken stretches: ×1.1 from 15 min, rising
  to ×2.0 at four hours. One long session beats the same time chopped up.
- **Streak bonus** adds 5% per consecutive day, capped at +50% — enough to
  protect a habit, not so much that breaking one is a catastrophe.
- **Broken sessions still pay**, at 25% of base points with no bonuses. A bad
  run is not wasted time, but finishing always beats quitting.

A day counts toward your streak once it contains a completed session of at
least 10 minutes. Today not being earned *yet* never breaks a streak — the day
is not over.

## Architecture

Two modules, split along a deliberate line: everything that decides what the
user earns is pure Kotlin and unit-tested, and the Android module is left as a
thin shell around it.

```
core/   Pure Kotlin/JVM. No Android dependencies.
        AwayMode, ScoreEngine, StreakCalculator, LevelSystem,
        SessionRules (the break rules as a pure state machine),
        SessionFactory (turns a finished run into a scored record).

app/    Android + Jetpack Compose.
        IdleSessionService  foreground service owning the clock; funnels every
                            screen/sensor/lifecycle event through SessionRules.
        StillnessDetector   accelerometer watch for Hands Off mode.
        SessionRepository   JSON-file history; points are always derived from
                            it, so no total can drift out of sync.
        ui/                 Compose screens (home, active session, history).
```

Design notes worth knowing:

- **Time is measured with `SystemClock.elapsedRealtime()`**, not by counting
  ticks. A tick deferred by Doze costs display smoothness, never credit.
- **A killed process resumes its session.** The active run is checkpointed to
  SharedPreferences; a stored clock base in the future proves a reboot, and
  those sessions are dropped rather than credited with bogus time.
- **Break rules live in `core`** so the logic deciding whether somebody keeps an
  hour of credit is testable without a device.

## Building

Open in Android Studio, or from the command line with an Android SDK present:

```bash
./gradlew :app:assembleDebug     # build the APK
./gradlew :core:test             # run the domain tests
```

`settings.gradle.kts` only includes `:app` when an Android SDK is detected
(`local.properties`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT`). On a machine
without one, `gradle :core:test` still works — the domain logic has no Android
dependencies.

- minSdk 26, targetSdk 35
- Kotlin 2.0.21, Compose BOM 2024.12.01, AGP 8.7.3

## Status

`:core` is compiled and covered by 49 unit tests, including the scoring curve,
streak edges, the per-mode break rules, and end-to-end session simulations.

`:app` has not been compiled — it was written in an environment where the
Android SDK could not be downloaded. Expect to fix small things on the first
build in Android Studio.

## Ideas not built yet

- Redeeming points for rewards you define yourself ("500 pts = 30 min gaming").
- A "peek budget": allow N screen wakes per session before it counts as a break.
- Scheduled sessions, so the phone is automatically off-limits at set times.
- Whitelisting: let a call from a specific contact through without breaking a run.
