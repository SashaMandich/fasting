# Fasting

A local-first Android intermittent-fasting tracker. It times your fast against a goal, maps the
elapsed time onto biological stages, and correlates fasting effort against weight trend and how you
actually felt.

Everything stays on the device. There is no account, no backend, no analytics SDK, and no network
permission in the manifest — the only permissions requested are `POST_NOTIFICATIONS` and
`RECEIVE_BOOT_COMPLETED`.

## What it does

**Goal-driven timer.** Pick 16-8, 18-6, 20-4, or Open. A goal is a wall-clock commitment, so a
target of 16h means sixteen real hours regardless of what you ate first. Once the target is met the
fast keeps counting up as overtime rather than stopping.

**Stage engine, scaled by the pre-fast meal.** A fast moves through five phases — Digestive,
Glycogen Depletion, Early Autophagy, Deep Autophagy, and Extended Repair. Unlike the goal, these
boundaries *do* move with what you ate: a carb-heavy meal refills liver glycogen and pushes every
transition ~20% later, a keto meal pulls them ~20% earlier. A keto pre-fast meal reaches Early
Autophagy at 14.4h instead of 18h.

That distinction is the core modelling decision in the app. A goal is a promise the user made; a
stage is a metabolic estimate. They are deliberately not the same number.

**Weight tracking with an EMA trendline.** Weights are always persisted in kilograms and converted
for display. Multiple weigh-ins on the same day are averaged first, then the exponential moving
average advances one *measured* day at a time — a skipped weigh-in is missing data, not evidence of
no change. Entries are tagged (Morning Fasted / Post-Refeed / Standard) so you can compare like with
like.

**Subjective logging.** Energy, clarity, and hunger on 1–5 scales, plus symptom flags (headache,
cravings, mental clarity, euphoria, …). Each log records how deep into the fast it was taken, which
is what lets the insights screen plot symptoms against elapsed hours and group mean scores by meal
composition.

**Insights.** Phase distribution, monthly deep-autophagy ratio (autophagic hours over total fasted
hours), a commitment calendar heatmap, weight-versus-cumulative-autophagy scatter, and symptom
correlation charts. All charts are drawn directly on Compose `Canvas` — no charting dependency.

**Home-screen widget.** A Glance widget showing live elapsed time and current stage, with start/stop
actions. Stage transitions fire notifications via `AlarmManager`, rescheduled on boot and on package
replace.

## Screens

Five destinations in a bottom nav bar: **Timer**, **Weight**, **Insights**, **Log**, **Guide**.

## Architecture

```
domain/     Pure Kotlin. No Android imports, no coroutines — this is what the tests cover.
            FastStage, MealComposition, StageEngine, FastingGoal/GoalEngine,
            AnalyticsEngine, WeightTrend.
data/       Room database (schema v2, exported to app/schemas/), repository,
            SharedPreferences-backed SettingsStore.
tracking/   FastTracker, notifications, AlarmManager stage scheduling, boot receiver.
ui/         Compose screens, ViewModels, hand-rolled Canvas charts.
widget/     Glance app widget.
```

The domain layer is deliberately free of framework dependencies, so the stage maths, goal
arithmetic, autophagy attribution, and EMA smoothing are all testable as plain JVM unit tests.

Preferences live in `SharedPreferences` rather than Room because broadcast receivers and widget
callbacks need synchronous reads and can't collect a flow.

Navigation is five destinations held in `rememberSaveable` local state rather than a nav graph —
there is no argument passing between leaf screens to justify the indirection.

## Build

Requires JDK 17 and the Android SDK (compileSdk 37, minSdk 31).

```bash
./gradlew test          # 38 JVM unit tests over the domain layer
./gradlew assembleDebug
./gradlew installDebug  # onto a connected device or running emulator
```

### Release signing

Release builds read signing credentials from `keystore.properties` at the repo root, which is
gitignored along with `*.jks`. **That file is not in this repository and neither is the keystore.**

A missing `keystore.properties` is not a build error — debug builds and fresh checkouts work fine,
and the release APK is simply left unsigned. To sign your own builds, create the keystore and a
matching properties file:

```properties
storeFile=your-release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Note that `isMinifyEnabled` is currently `false` for release. Room, Glance, and Compose all need
keep rules, and a class stripped by R8 only fails at runtime — so shrinking stays off until a
minified build has been exercised on a real device.

## Tech

Kotlin 2.4, AGP 9.3, Jetpack Compose (Material 3), Room 2.8 with KSP, Glance 1.2 for the widget,
kotlinx-coroutines. No dependency injection framework and no charting library.
