# Kalist training app

Local calisthenics training planner and tracker (Android).

## Project

- Spec: `claude/calisthenics-app-spec.md` (build order in section 10 is done: steps 1–7).
- Kotlin + Jetpack Compose + Material 3, Hilt, Room (schema in `app/schemas`, migrations in
  `KalistDatabase.MIGRATIONS`, never destructive), DataStore for settings. No INTERNET permission.
- Strings: French in `res/values*` (default), English in `res/values-en`. Add every new string to both.
- Packages: `data/db` (entities, DAOs), `data/repo`, `data/backup` (JSON + CSV/XLSX journal),
  `domain` (pure logic: circuit, progression, next template, stats), `ui/<feature>`.

## Build and test locally

- Needs the Android SDK at `/opt/android-sdk` (`local.properties`); install with
  `sdkmanager "platforms;android-37.0" "build-tools;37.0.0"` if missing.
- `./gradlew assembleDebug testDebugUnitTest` (unit, Room DAO, migration, Robolectric UI tests).
- UI tests save screenshots to `app/build/screenshots/` for visual review.
- Robolectric gotchas: seed/block on the DB before launching the activity, never while it runs;
  a TextField inside an AlertDialog keeps Compose "busy" in Robolectric.

## APK builds

- `.github/workflows/build-apk.yml` builds a debug APK on every push and publishes it
  to the rolling GitHub Release `apk-latest`.
- Stable download link (always the latest successful build):
  https://github.com/Giing/kalist-training-app/releases/download/apk-latest/kalist.apk
- `ci/debug.keystore` is a shared debug signing key (store/key password `android`,
  alias `androiddebugkey`) so successive builds install as updates over each other.
  Do not replace it, or the user must uninstall the app (losing local data) to update.

## Standing instruction from the user

After every important update (new feature, milestone of the build order, notable fix):
1. Commit and push.
2. Check the "Build APK" workflow run for that commit until it finishes.
3. If it succeeded, give the user the download link above (plus the short commit SHA).
   If it failed, read the job logs, fix the problem, and repeat.
